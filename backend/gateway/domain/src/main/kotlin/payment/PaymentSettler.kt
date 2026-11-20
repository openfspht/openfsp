package org.openfsp.gateway.payment

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.spi.OperatorSignals
import org.openfsp.gateway.common.spi.Transactions
import org.openfsp.gateway.payment.api.AttestPayment
import org.openfsp.gateway.payment.api.CallbackOutcome
import org.openfsp.gateway.payment.api.SettlePayment
import org.openfsp.gateway.payment.spi.CallbackUrls
import org.openfsp.gateway.payment.spi.PaymentJournal
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.provider.CallbackRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.VerifiedCallback
import org.openfsp.gateway.provider.boundTo
import org.openfsp.gateway.provider.isAddressedBy
import org.openfsp.gateway.provider.spi.Providers
import org.openfsp.gateway.webhook.api.RecordPaymentEvents
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.ProblemType
import java.net.URI

@DomainService
class PaymentSettler(
    private val payments: Payments,
    private val journal: PaymentJournal,
    private val events: RecordPaymentEvents,
    private val providers: Providers,
    private val transactions: Transactions,
    private val signals: OperatorSignals,
    private val tokens: CallbackTokens,
    private val calendar: BusinessCalendar,
    private val callbackUrls: CallbackUrls,
) : SettlePayment,
    AttestPayment {
    override fun apply(
        paymentId: String,
        report: ProviderReport,
        source: TransitionSource,
    ): Payment =
        transactions.inTransaction {
            val payment = payments.lock(paymentId) ?: throw OpenFspException.notFound()
            if (!payment.isTerminal && lacksRequiredAmount(payment, report)) {
                signals.signal(payment.id, "${source.wire} reported succeeded without the amount the provider reports: the payment stays ${payment.status.wire}")
                return@inTransaction payment
            }
            when (val observation = payment.observe(report, source, calendar.now())) {
                is Observation.Transitioned -> {
                    journal.record(observation.transition)
                    payments.save(observation.payment).also { events.record(PaymentTransitioned(payment.id, observation.transition.from, observation.transition.to)) }
                }
                is Observation.Updated -> payments.save(observation.payment)
                is Observation.Conflicted -> {
                    journal.record(observation.conflict)
                    signals.signal(
                        payment.id,
                        "conflict: ${source.wire} reported ${report.status.wire} for ${report.amount?.amount ?: payment.amount.amount} cents, the payment is ${payment.status.wire}",
                    )
                    payment
                }
                Observation.Unchanged -> payment
            }
        }

    override fun onSignedCallback(
        providerId: String,
        request: CallbackRequest,
    ): CallbackOutcome {
        val provider = providers.platform(providerId) ?: return CallbackOutcome.UnknownProvider
        val verified =
            provider.verifiedCallback(request.targeting(callbackUrls.forProvider(providerId, null)))
                ?: return CallbackOutcome.Unverified.also { signals.signal(providerId, "rejected a callback: signature not verified") }
        val report = (verified as? VerifiedCallback.Reported)?.report ?: return CallbackOutcome.Acknowledged
        val payment = payments.withOrder(providerId, report.orderId)?.takeIf { it.isAddressedBy(null) } ?: return CallbackOutcome.UnknownPayment
        return CallbackOutcome.Applied(applyConfirmed(payment, report, TransitionSource.SIGNED_CALLBACK))
    }

    override fun onPaymentUrlCallback(
        providerId: String,
        token: String,
        request: CallbackRequest,
    ): Payment? =
        paymentUrlCallback(providerId, token, request.targeting(callbackUrls.forProvider(providerId, token)))
            ?: null.also { signals.signal(providerId, "rejected a callback on a payment URL: unknown token, terminal payment, bad signature or unreadable body") }

    override fun returnTargetOf(
        providerId: String,
        token: String,
    ): URI? = withToken(providerId, token)?.returnUrl

    private fun withToken(
        providerId: String,
        token: String,
    ): Payment? = payments.withCallbackToken(providerId, tokens.digest(token))?.takeIf { tokens.matches(token, it.callbackTokenDigest) }

    private fun paymentUrlCallback(
        providerId: String,
        token: String,
        request: CallbackRequest,
    ): Payment? {
        val payment = withToken(providerId, token)?.takeIf { !it.isTerminal } ?: return null
        val provider = providers.boundTo(payment)?.takeIf { it.descriptor.paymentUrl } ?: return null
        val signed = provider.descriptor.signedCallbacks
        val verified = (if (signed) provider.verifiedCallback(request) else provider.unsignedCallback(request)?.let(VerifiedCallback::Reported)) ?: return null
        val report = (verified as? VerifiedCallback.Reported)?.report ?: return payment
        if (report.orderId != payment.providerOrderId) return null
        return applyConfirmed(payment, report, if (signed) TransitionSource.SIGNED_CALLBACK else TransitionSource.PER_PAYMENT_URL)
    }

    private fun applyConfirmed(
        payment: Payment,
        report: ProviderReport,
        source: TransitionSource,
    ): Payment {
        val provider = providers.boundTo(payment)
        if (payment.isTerminal || !lacksRequiredAmount(payment, report) || provider?.descriptor?.hasLookup != true) return apply(payment.id, report, source)
        val confirmed = runCatching { provider.lookup(payment.providerOrderId) }.getOrElse { return apply(payment.id, report, source) }
        return apply(payment.id, confirmed, TransitionSource.LOOKUP)
    }

    private fun lacksRequiredAmount(
        payment: Payment,
        report: ProviderReport,
    ): Boolean = report.status == PaymentStatus.SUCCEEDED && report.amount == null && providers.boundTo(payment)?.descriptor?.reportsAmount == true

    override fun attest(
        paymentId: String,
        attestation: Attestation,
    ): Payment =
        transactions.inTransaction {
            val payment = payments.lock(paymentId) ?: throw OpenFspException.notFound()
            if (payment.isTerminal) throw OpenFspException(ProblemType.STATE_CONFLICT, "The payment is already ${payment.status.wire}")
            journal.record(paymentId, attestation, calendar.now())
            signals.signal(paymentId, "attested ${attestation.status.wire} by ${attestation.person}")
            apply(
                paymentId,
                ProviderReport(payment.providerOrderId, payment.providerReference, attestation.status, payment.amount, attestation.failureReason),
                TransitionSource.ATTESTATION,
            )
        }
}
