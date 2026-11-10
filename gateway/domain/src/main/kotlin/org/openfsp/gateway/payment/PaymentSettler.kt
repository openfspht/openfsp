package org.openfsp.gateway.payment

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.spi.OperatorSignals
import org.openfsp.gateway.common.spi.Transactions
import org.openfsp.gateway.payment.api.AttestAPayment
import org.openfsp.gateway.payment.api.CallbackOutcome
import org.openfsp.gateway.payment.api.SettleAPayment
import org.openfsp.gateway.payment.spi.PaymentEvents
import org.openfsp.gateway.payment.spi.PaymentJournal
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.spi.Providers

@DomainService
class PaymentSettler(
    private val payments: Payments,
    private val journal: PaymentJournal,
    private val events: PaymentEvents,
    private val providers: Providers,
    private val transactions: Transactions,
    private val signals: OperatorSignals,
    private val tokens: CallbackTokens,
    private val calendar: BusinessCalendar,
) : SettleAPayment,
    AttestAPayment {
    override fun apply(
        paymentId: String,
        report: ProviderReport,
        source: TransitionSource,
    ): Payment =
        transactions.inTransaction {
            val payment = payments.lock(paymentId) ?: throw OpenFspException.notFound()
            when (val observation = payment.observe(report, source, calendar.now())) {
                is Observation.Transitioned -> {
                    journal.record(observation.transition)
                    events.publish(PaymentTransitioned(payment.id, observation.transition.from, observation.transition.to))
                    payments.save(observation.payment)
                }
                is Observation.Updated -> payments.save(observation.payment)
                is Observation.Conflicted -> {
                    journal.record(observation.conflict)
                    signals.signal(
                        payment.id,
                        "conflict: ${source.wire} reported ${report.status.wire} for ${report.amount.amount} cents, the payment is ${payment.status.wire}",
                    )
                    payment
                }
                Observation.Unchanged -> payment
            }
        }

    override fun onSignedCallback(
        providerId: String,
        headers: (String) -> String?,
        body: ByteArray,
    ): CallbackOutcome {
        val provider = providers[providerId] ?: return CallbackOutcome.UnknownProvider
        val report = provider.verifiedCallback(headers, body) ?: return CallbackOutcome.Unverified
        val payment = payments.withOrder(providerId, report.orderId) ?: return CallbackOutcome.UnknownPayment
        return CallbackOutcome.Applied(apply(payment.id, report, TransitionSource.SIGNED_CALLBACK))
    }

    override fun onPaymentUrlCallback(
        providerId: String,
        token: String,
        body: ByteArray,
    ): Payment? {
        val provider = providers[providerId]?.takeIf { it.descriptor.hasPaymentUrl } ?: return null
        val payment = payments.withCallbackToken(providerId, tokens.digest(token)) ?: return null
        if (!tokens.matches(token, payment.callbackTokenDigest) || payment.isTerminal) return null
        val report = provider.unsignedCallback(body)?.takeIf { it.orderId == payment.providerOrderId } ?: return null
        return apply(payment.id, report, TransitionSource.PAYMENT_URL)
    }

    override fun attest(
        paymentId: String,
        attestation: Attestation,
    ): Payment =
        transactions.inTransaction {
            val payment = payments.lock(paymentId) ?: throw OpenFspException.notFound()
            journal.record(paymentId, attestation, calendar.now())
            signals.signal(paymentId, "attested ${attestation.status.wire} by ${attestation.person}")
            apply(
                paymentId,
                ProviderReport(payment.providerOrderId, payment.providerReference, attestation.status, payment.amount, attestation.failureReason),
                TransitionSource.ATTESTATION,
            )
        }
}
