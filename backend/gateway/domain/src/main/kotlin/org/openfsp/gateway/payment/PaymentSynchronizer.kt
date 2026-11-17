package org.openfsp.gateway.payment

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.spi.OperatorSignals
import org.openfsp.gateway.payment.api.ReconcilePendingPayments
import org.openfsp.gateway.payment.api.SettleAPayment
import org.openfsp.gateway.payment.api.SynchronizeAPayment
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.provider.spi.Providers
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.ProblemType
import java.time.Duration

@DomainService
class PaymentSynchronizer(
    private val payments: Payments,
    private val providers: Providers,
    private val settlement: SettleAPayment,
    private val signals: OperatorSignals,
    private val calendar: BusinessCalendar,
) : SynchronizeAPayment,
    ReconcilePendingPayments {
    override fun synchronize(paymentId: String): Payment {
        val payment = payments.find(paymentId) ?: throw OpenFspException.notFound()
        if (payment.isTerminal) return payment
        val provider = providers[payment.provider] ?: throw OpenFspException(ProblemType.PROVIDER_UNAVAILABLE)
        if (!provider.descriptor.hasLookup) throw OpenFspException(ProblemType.CAPABILITY_NOT_SUPPORTED, "${provider.id} does not offer payments.lookup")
        return try {
            lookUp(provider, payment)
        } catch (failure: ProviderFailure) {
            throw failure.toOpenFsp()
        }
    }

    override fun reconcile(batch: Int): Int {
        val now = calendar.now()
        val withLookup = providers.all().filter { it.descriptor.hasLookup }.associateBy { it.id }
        return payments
            .dueForReconciliation(withLookup.keys, now.minus(QUIET_PERIOD), now.minus(Payment.CREATION_LEASE), batch)
            .count { payment ->
                val provider = withLookup.getValue(payment.provider)
                runCatching { lookUp(provider, payment) }
                    .onFailure { signals.signal(payment.id, "reconciliation postponed: ${it.message}") }
                    .also { payments.markChecked(payment.id, calendar.now()) }
                    .isSuccess
            }
    }

    private fun lookUp(
        provider: ProviderOperations,
        payment: Payment,
    ): Payment {
        val sentAt = calendar.now()
        val report = provider.lookup(payment.providerOrderId)
        val observed = if (report.status == PaymentStatus.PENDING && payment.isExpiredAt(sentAt)) report.copy(status = PaymentStatus.EXPIRED) else report
        return settlement.apply(payment.id, observed, TransitionSource.LOOKUP)
    }

    private companion object {
        val QUIET_PERIOD: Duration = Duration.ofSeconds(30)
    }
}
