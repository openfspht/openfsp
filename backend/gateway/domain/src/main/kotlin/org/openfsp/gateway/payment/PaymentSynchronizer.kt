package org.openfsp.gateway.payment

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.spi.OperatorSignals
import org.openfsp.gateway.payment.api.CallbackOutcome
import org.openfsp.gateway.payment.api.NotifyPayment
import org.openfsp.gateway.payment.api.ReconcilePendingPayments
import org.openfsp.gateway.payment.api.ReturnPayer
import org.openfsp.gateway.payment.api.SettlePayment
import org.openfsp.gateway.payment.api.SynchronizePayment
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.payment.spi.SynchronizationLimits
import org.openfsp.gateway.provider.addressed
import org.openfsp.gateway.provider.boundTo
import org.openfsp.gateway.provider.isAddressedBy
import org.openfsp.gateway.provider.servedOrNull
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.provider.spi.Providers
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.ProblemType
import java.net.URI

@DomainService
class PaymentSynchronizer(
    private val payments: Payments,
    private val providers: Providers,
    private val settlement: SettlePayment,
    private val signals: OperatorSignals,
    private val calendar: BusinessCalendar,
    private val limits: SynchronizationLimits,
) : SynchronizePayment,
    ReconcilePendingPayments,
    ReturnPayer,
    NotifyPayment {
    override fun current(
        account: String,
        paymentId: String,
    ): Payment {
        val payment = payments.findOfAccount(account, paymentId)?.takeIf { it.isVisible } ?: throw OpenFspException.notFound()
        val provider = providers.boundTo(payment)?.takeIf { it.descriptor.hasLookup } ?: return payment
        if (payment.isTerminal || limits.tryAcquire(payment.id) != null) return payment
        return runCatching { lookUp(provider, payment) }
            .onFailure { signals.signal(payment.id, "lookup on read postponed: ${it.message}") }
            .getOrDefault(payment)
    }

    override fun returnTarget(
        providerId: String,
        account: String?,
        parameters: Map<String, String>,
    ): URI? {
        val provider = providers.addressed(providerId, account) ?: return null
        val orderId = runCatching { orderOf(provider, account, parameters) }.getOrNull() ?: return null
        val payment = payments.withOrder(providerId, orderId)?.takeIf { it.isAddressedBy(account) } ?: return null
        val target = payment.returnUrl ?: return null
        val bound = providers.boundTo(payment)?.takeIf { it.descriptor.hasLookup } ?: return target
        if (!payment.isTerminal && limits.tryAcquire(payment.id) == null) runCatching { lookUp(bound, payment) }
        return target
    }

    override fun onLookupNotification(
        providerId: String,
        account: String?,
        parameters: Map<String, String>,
    ): CallbackOutcome? {
        val descriptor = providers.servedOrNull(providerId)?.descriptor ?: return CallbackOutcome.UnknownProvider
        if (!descriptor.lookupNotifications) return null
        val provider = providers.addressed(providerId, account) ?: return CallbackOutcome.UnknownProvider
        return try {
            val orderId = orderOf(provider, account, parameters) ?: return CallbackOutcome.UnknownPayment
            val payment = payments.withOrder(providerId, orderId)?.takeIf { it.isAddressedBy(account) } ?: return CallbackOutcome.UnknownPayment
            if (payment.isTerminal || limits.tryAcquire(payment.id) != null) return CallbackOutcome.Applied(payment)
            val bound = providers.boundTo(payment) ?: return CallbackOutcome.Applied(payment).also { signals.signal(payment.id, "notification not looked up: $UNUSABLE_CREDENTIALS") }
            CallbackOutcome.Applied(lookUp(bound, payment))
        } catch (failure: ProviderFailure) {
            throw failure.toOpenFsp()
        }
    }

    override fun reconcile(batch: Int): Int {
        val now = calendar.now()
        val withLookup = providers.served().filter { it.descriptor.hasLookup }.map { it.id }.toSet()
        if (withLookup.isEmpty()) return 0
        val due = ReconciliationDue(withLookup, now, now.minus(ReconciliationTurn.FIRST_DELAY), now.minus(Payment.CREATION_LEASE), now.plus(ReconciliationTurn.CLAIM))
        return payments
            .claimForReconciliation(due, batch)
            .count { turn ->
                val payment = turn.payment
                runCatching { lookUp(providers.boundTo(payment) ?: throw ProviderFailure.Unavailable(message = UNUSABLE_CREDENTIALS), payment) }
                    .onFailure { signals.signal(payment.id, "reconciliation postponed: ${it.message}") }
                    .also { payments.scheduleCheck(payment.id, calendar.now().plus(turn.delayAfter), turn.nextAttempts) }
                    .isSuccess
            }
    }

    private fun orderOf(
        provider: ProviderOperations,
        account: String?,
        parameters: Map<String, String>,
    ): String? {
        provider.returnedOrder(parameters)?.let { return it }
        limits.tryResolve("${provider.id}/${account.orEmpty()}")?.let { throw OpenFspException(ProblemType.RATE_LIMITED, retryAfter = it) }
        return provider.transactionOrder(parameters)
    }

    private fun lookUp(
        provider: ProviderOperations,
        payment: Payment,
    ): Payment {
        val sentAt = calendar.now()
        val report = provider.lookup(payment.providerOrderId)
        val expiredByGuarantee = report.status == PaymentStatus.PENDING && provider.descriptor.expiryGuaranteed && payment.isExpiredAt(sentAt)
        val observed = if (expiredByGuarantee) report.copy(status = PaymentStatus.EXPIRED) else report
        return settlement.apply(payment.id, observed, TransitionSource.LOOKUP)
    }

    private companion object {
        const val UNUSABLE_CREDENTIALS = "the credentials of the payment are no longer usable"
    }
}
