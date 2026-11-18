package org.openfsp.simulator.settlement

import org.openfsp.ddd.DomainService
import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.common.SimulationSettings
import org.openfsp.simulator.operator.spi.OperatorProfiles
import org.openfsp.simulator.payment.Outcome
import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.payment.spi.SimulatedPayments
import org.openfsp.simulator.scenario.Scenario
import org.openfsp.simulator.settlement.spi.CallbackSender
import org.openfsp.simulator.settlement.spi.Timers
import org.openfsp.simulator.statement.StatementLine
import org.openfsp.simulator.statement.spi.StatementBook
import java.time.temporal.ChronoUnit

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
        var settledNow = false
        val settled =
            payments.update(paymentId) { current ->
                if (current.isSettled) current else current.settle(outcome, reason, clock.now()).also { settledNow = true }
            } ?: return null
        if (!settledNow) return settled
        if (settled.outcome == Outcome.SUCCESSFUL && profiles[settled.operator]?.statements == true) {
            statements.book(
                settled.operator,
                clock.localNow().toLocalDate(),
                StatementLine(settled.paymentId, settled.reference, PAYMENT, (settled.reportedAmount ?: settled.amount).toBigDecimal(), clock.localNow().truncatedTo(ChronoUnit.SECONDS)),
            )
        }
        when (settled.scenario) {
            Scenario.NO_CALLBACK, Scenario.UNKNOWN_STATUS -> Unit
            Scenario.DUPLICATE_CALLBACK -> repeat(DUPLICATES) { callBack(paymentId) }
            Scenario.LATE_CALLBACK -> timers.after(settings.lateCallbackDelay) { callBack(paymentId) }
            else -> callBack(paymentId)
        }
        return payments.find(paymentId)
    }

    fun expireIfDue(payment: SimulatedPayment): SimulatedPayment =
        if (!payment.isSettled && clock.now().isAfter(payment.expiresAt)) resolve(payment.paymentId, Outcome.EXPIRED, null) ?: payment else payment

    fun callBack(paymentId: String) {
        val payment = payments.find(paymentId) ?: return
        val url = payment.callbackUrl ?: return
        val outcome = payment.outcome ?: return
        val callback = OperatorCallback(payment.paymentId, payment.reference, outcome.wire, payment.reportedAmount ?: payment.amount, payment.reason, payment)
        if (sender.send(payment.operator, url, callback)) {
            payments.update(payment.paymentId) { it.copy(callbacksSent = it.callbacksSent + 1) }
        }
    }

    private companion object {
        const val PAYMENT = "PAYMENT"
        const val DUPLICATES = 3
    }
}
