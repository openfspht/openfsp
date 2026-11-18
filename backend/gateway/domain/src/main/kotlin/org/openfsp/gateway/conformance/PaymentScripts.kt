package org.openfsp.gateway.conformance

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.conformance.spi.CallbackDelivery
import org.openfsp.gateway.conformance.spi.Deferrals
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.provider.CallbackRequest
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.provider.ProviderPayment
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.protocol.NextAction
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap

@DomainService
class PaymentScripts(
    private val payments: Payments,
    private val calendar: BusinessCalendar,
    private val deferrals: Deferrals,
    private val callbacks: CallbackDelivery,
    private val settings: ConformanceSettings,
) {
    private data class Declaration(
        val run: String,
        val reference: String,
    )

    private val random = SecureRandom()
    private val runs = ConcurrentHashMap<String, ConformanceRun>()
    private val declared = ConcurrentHashMap<Declaration, Scenario>()
    private val played = ConcurrentHashMap<ScriptedOrder, ScriptedPayment>()

    fun open(run: ConformanceRun) {
        runs[run.account] = run
    }

    fun runOf(account: String): ConformanceRun? = runs[account]?.takeIf { it.isActiveAt(calendar.now()) }

    fun declare(
        run: ConformanceRun,
        reference: String,
        scenario: Scenario,
    ) {
        declared[Declaration(run.account, reference)] = scenario
    }

    fun of(
        run: ConformanceRun,
        payment: Payment,
    ): ScriptedPayment? = played[ScriptedOrder(payment.provider, payment.providerOrderId)]?.takeIf { it.run == run.account }

    fun create(
        provider: ProviderOperations,
        request: ProviderPaymentRequest,
    ): ProviderPayment? {
        val order = ScriptedOrder(provider.id, request.orderId)
        played[order]?.let { return if (it.accepted) it.presented() else throw refusal() }
        if (declared.isEmpty()) return null
        val script = script(provider, request, order) ?: return null
        played.putIfAbsent(order, script)?.let { return if (it.accepted) it.presented() else throw refusal() }
        if (!script.accepted) throw refusal()
        script.scenario.outcome?.let { outcome -> deferrals.after(settings.settleAfter) { resolve(order, outcome) } }
        if (script.scenario == Scenario.EXPIRE) deferrals.after(EXPIRY_WINDOW) { expireIfDue(order) }
        if (script.scenario == Scenario.TIMEOUT) throw ProviderFailure.Timeout()
        return script.presented()
    }

    fun lookup(
        provider: String,
        orderId: String,
    ): ProviderReport? {
        val order = ScriptedOrder(provider, orderId)
        val script = played[order] ?: return null
        if (!script.accepted) throw ProviderFailure.Rejected("The provider knows no payment for this order")
        return (expireIfDue(order) ?: script).lookedUp()
    }

    fun notified(
        provider: String,
        request: CallbackRequest,
    ): ProviderReport? {
        val presented = request.headers(NOTIFICATION_HEADER)?.toByteArray() ?: return null
        val script = played.values.firstOrNull { it.order.provider == provider && MessageDigest.isEqual(it.secret.toByteArray(), presented) }
        return script?.takeIf { it.isSettled }?.report()
    }

    fun resolve(
        order: ScriptedOrder,
        outcome: ScriptedOutcome,
    ): ScriptedPayment? {
        var settledNow = false
        val script =
            played.computeIfPresent(order) { _, current ->
                current.settle(outcome).also { settledNow = it !== current }
            } ?: return null
        if (settledNow) {
            when (script.scenario.notifications) {
                Notifications.NONE -> Unit
                Notifications.ONCE -> deferrals.after(Duration.ZERO) { callBack(order) }
                Notifications.REPEATED -> deferrals.after(Duration.ZERO) { repeat(REPETITIONS) { callBack(order) } }
                Notifications.LATE -> deferrals.after(settings.lateCallbackAfter) { callBack(order) }
            }
        }
        return script
    }

    fun callBack(order: ScriptedOrder) {
        val script = played[order]?.takeIf { it.isSettled && it.notifies } ?: return
        val request = CallbackRequest(headers = { name -> script.secret.takeIf { name.equals(NOTIFICATION_HEADER, ignoreCase = true) } }, parameters = emptyMap(), body = ByteArray(0))
        callbacks.deliver(order.provider, script.callbackToken, request)
        played.computeIfPresent(order) { _, current -> current.copy(callbacksSent = current.callbacksSent + 1) }
    }

    fun sweep() {
        val now = calendar.now()
        runs.values.removeIf { !it.isActiveAt(now) }
        declared.keys.removeIf { !runs.containsKey(it.run) }
        played.values.removeIf { !runs.containsKey(it.run) }
    }

    private fun expireIfDue(order: ScriptedOrder): ScriptedPayment? =
        played[order]?.let { if (it.isDueToExpireAt(calendar.now())) resolve(order, ScriptedOutcome.EXPIRED) else it }

    private fun script(
        provider: ProviderOperations,
        request: ProviderPaymentRequest,
        order: ScriptedOrder,
    ): ScriptedPayment? {
        val payment = payments.withOrder(order.provider, order.orderId) ?: return null
        val run = runOf(payment.accountId) ?: return null
        val scenario = declared[Declaration(run.account, payment.reference)] ?: return null
        val descriptor = provider.descriptor
        val providerReference = REFERENCE_PREFIX + hex(REFERENCE_BYTES)
        val expiresAt = if (scenario == Scenario.EXPIRE) payment.createdAt.plus(EXPIRY_WINDOW) else request.expiresAt
        return ScriptedPayment(
            run = run.account,
            order = order,
            scenario = scenario,
            amount = request.amount,
            providerReference = providerReference,
            secret = hex(SECRET_BYTES),
            callbackToken = request.callbackUrl.path.substringAfterLast('/').takeIf { descriptor.paymentUrl },
            notifies = descriptor.signedCallbacks || descriptor.paymentUrl,
            hasLookup = descriptor.hasLookup,
            reportsFee = ProviderDescriptor.FEE in descriptor.capabilities,
            nextAction = nextAction(descriptor, providerReference, expiresAt),
            expiresAt = expiresAt,
            accepted = scenario != Scenario.PROVIDER_ERROR,
        )
    }

    private fun nextAction(
        descriptor: ProviderDescriptor,
        providerReference: String,
        expiresAt: Instant?,
    ): NextAction =
        if (descriptor.nextActions == setOf(NextAction.Type.REDIRECT)) {
            NextAction.redirect("${settings.controlEndpoint}$CHECKOUT$providerReference", expiresAt)
        } else {
            NextAction.payerApproval
        }

    private fun refusal() = ProviderFailure.Rejected("The scripted payment service answered an error", mapOf(CODE to ERROR_CODE, MESSAGE to ERROR_MESSAGE))

    private fun hex(bytes: Int): String = HexFormat.of().formatHex(ByteArray(bytes).also(random::nextBytes))

    companion object {
        const val CHECKOUT = "/checkout/"
        const val NOTIFICATION_HEADER = "OpenFSP-Scripted-Notification"
        val EXPIRY_WINDOW: Duration = Duration.ofMinutes(1)
        private const val REFERENCE_PREFIX = "SCRIPTED-"
        private const val REFERENCE_BYTES = 8
        private const val SECRET_BYTES = 32
        private const val REPETITIONS = 3
        private const val CODE = "code"
        private const val MESSAGE = "message"
        private const val ERROR_CODE = "INTERNAL"
        private const val ERROR_MESSAGE = "Scripted provider error"
    }
}
