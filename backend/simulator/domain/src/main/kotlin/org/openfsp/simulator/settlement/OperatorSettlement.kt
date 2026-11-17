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
import org.openfsp.simulator.space.SpaceId
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
    fun schedule(
        space: SpaceId,
        payment: SimulatedPayment,
    ) {
        val (outcome, reason) = payment.scenario.settlesBy ?: return
        timers.after(settings.settleAfter) { resolve(space, payment.paymentId, outcome, reason) }
    }

    fun resolve(
        space: SpaceId,
        paymentId: String,
        outcome: Outcome,
        reason: String?,
    ): SimulatedPayment? {
        var settledNow = false
        val settled =
            payments.update(space, paymentId) { current ->
                if (current.isSettled) current else current.settle(outcome, reason, clock.now()).also { settledNow = true }
            } ?: return null
        if (!settledNow) return settled
        if (settled.outcome == Outcome.SUCCESSFUL && profiles[settled.operator]?.statements == true) {
            statements.book(
                space,
                settled.operator,
                clock.localNow().toLocalDate(),
                StatementLine(settled.paymentId, settled.orderId, PAYMENT, (settled.reportedAmount ?: settled.amount).toBigDecimal(), clock.localNow().truncatedTo(ChronoUnit.SECONDS)),
            )
        }
        when (settled.scenario) {
            Scenario.NO_CALLBACK, Scenario.UNKNOWN_STATUS -> Unit
            Scenario.DUPLICATE_CALLBACK -> repeat(DUPLICATES) { callBack(space, paymentId) }
            Scenario.LATE_CALLBACK -> timers.after(settings.lateCallbackDelay) { callBack(space, paymentId) }
            else -> callBack(space, paymentId)
        }
        return payments.find(space, paymentId)
    }

    fun expireIfDue(
        space: SpaceId,
        payment: SimulatedPayment,
    ): SimulatedPayment =
        if (!payment.isSettled && clock.now().isAfter(payment.expiresAt)) resolve(space, payment.paymentId, Outcome.EXPIRED, null) ?: payment else payment

    fun callBack(
        space: SpaceId,
        paymentId: String,
    ) {
        val payment = payments.find(space, paymentId) ?: return
        val url = payment.callbackUrl ?: return
        val outcome = payment.outcome ?: return
        val callback = OperatorCallback(payment.paymentId, payment.orderId, outcome.wire, payment.reportedAmount ?: payment.amount, payment.reason, payment, space)
        if (sender.send(payment.operator, url, callback)) {
            payments.update(space, payment.paymentId) { it.copy(callbacksSent = it.callbacksSent + 1) }
        }
    }

    private companion object {
        const val PAYMENT = "PAYMENT"
        const val DUPLICATES = 3
    }
}
