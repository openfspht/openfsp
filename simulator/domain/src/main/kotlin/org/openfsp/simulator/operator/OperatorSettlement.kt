package org.openfsp.simulator.operator

import org.openfsp.ddd.DomainService
import org.openfsp.simulator.operator.spi.CallbackSender
import org.openfsp.simulator.operator.spi.OperatorProfiles
import org.openfsp.simulator.operator.spi.SimulatedPayments
import org.openfsp.simulator.operator.spi.StatementBook
import org.openfsp.simulator.operator.spi.Timers

@DomainService
class OperatorSettlement(
    private val payments: SimulatedPayments,
    private val profiles: OperatorProfiles,
    private val statements: StatementBook,
    private val sender: CallbackSender,
    private val timers: Timers,
    private val clock: SimulationClock,
    private val settings: SimulationSettings,
) {
    fun schedule(payment: SimulatedPayment) {
        val (outcome, reason) = payment.scenario.settlesBy ?: return
        timers.after(settings.settleAfter) { resolve(payment.paymentId, outcome, reason) }
    }

    fun resolve(
        paymentId: String,
        outcome: Outcome,
        reason: String?,
    ): SimulatedPayment? {
        val before = payments.find(paymentId) ?: return null
        if (before.isSettled) return before
        val settled = payments.update(paymentId) { it.settle(outcome, reason) } ?: return null
        if (settled.outcome == Outcome.SUCCESSFUL && profiles[settled.operator]?.statements == true) {
            statements.book(
                settled.operator,
                clock.localNow().toLocalDate(),
                StatementLine(settled.paymentId, settled.orderId, PAYMENT, settled.reportedAmount ?: settled.amount, clock.localStamp()),
            )
        }
        when (settled.scenario) {
            Scenario.NO_CALLBACK, Scenario.UNKNOWN_STATUS -> Unit
            Scenario.DUPLICATE_CALLBACK -> repeat(DUPLICATES) { callBack(settled) }
            Scenario.LATE_CALLBACK -> timers.after(settings.lateCallbackDelay) { callBack(settled) }
            else -> callBack(settled)
        }
        return payments.find(paymentId)
    }

    fun expireIfDue(payment: SimulatedPayment): SimulatedPayment =
        if (!payment.isSettled && clock.now().isAfter(payment.expiresAt)) resolve(payment.paymentId, Outcome.EXPIRED, null) ?: payment else payment

    fun callBack(payment: SimulatedPayment) {
        val url = payment.callbackUrl ?: return
        val outcome = payment.outcome ?: return
        val callback = OperatorCallback(payment.paymentId, payment.orderId, outcome.wire, payment.reportedAmount ?: payment.amount, payment.reason)
        if (sender.send(payment.operator, url, callback)) {
            payments.update(payment.paymentId) { it.copy(callbacksSent = it.callbacksSent + 1) }
        }
    }

    private companion object {
        const val PAYMENT = "PAYMENT"
        const val DUPLICATES = 3
    }
}
