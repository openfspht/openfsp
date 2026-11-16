package org.openfsp.validator.engine

import org.openfsp.validator.engine.spi.SimulatorControl
import org.openfsp.validator.suite.JournalAssertion
import org.openfsp.validator.suite.SimulatorAction
import java.time.Duration
import java.time.Instant

class PreparationException(
    message: String,
) : RuntimeException(message)

data class JournalRecord(
    val method: String,
    val path: String,
    val body: String = "",
    val status: Int = 0,
    val responseBody: String = "",
)

class Simulator(
    private val control: SimulatorControl?,
) {
    fun perform(
        action: SimulatorAction,
        variables: Variables,
    ) {
        action.declare?.let {
            val operator = variables.resolve(it.operator)
            val order = variables.resolve(it.order)
            val scenario = variables.resolve(it.scenario)
            control().declareScenario(operator, order, scenario)
        }
        action.default?.let {
            val operator = variables.resolve(it.operator)
            val scenario = variables.resolve(it.scenario)
            control().defaultScenario(operator, scenario)
        }
        action.resolve?.let {
            val paymentId = paymentOf(variables.resolve(it.operator), variables.resolve(it.order))
            val outcome = variables.resolve(it.outcome)
            val reason = it.reason?.let(variables::resolve)
            control().resolve(paymentId, outcome, reason)
        }
        action.callback?.let {
            val paymentId = paymentOf(variables.resolve(it.operator), variables.resolve(it.order))
            control().callback(paymentId)
        }
        action.closeStatement?.let {
            val operator = variables.resolve(it.operator)
            val day = variables.resolve(it.day)
            control().closeStatement(operator, day)
        }
        action.statementEntry?.let {
            val operator = variables.resolve(it.operator)
            val day = variables.resolve(it.day)
            val amount = variables.resolve(it.amount)
            val bookedAt = variables.resolve(it.bookedAt)
            control().addStatementEntry(operator, day, amount, bookedAt)
        }
        action.settled?.let { settled(variables.resolve(it.operator), variables.resolve(it.order), it.callbacks, Durations.parse(it.within), Durations.parse(it.every)) }
        if (action.reset) control().reset()
    }

    fun journal(
        assertion: JournalAssertion,
        variables: Variables,
    ): List<JournalRecord> {
        val operator = variables.resolve(assertion.operator)
        val contains = assertion.contains?.let(variables::resolve)
        val records = mutableListOf<JournalRecord>()
        var page = 0
        do {
            val found = control().journal(operator, contains, page, PAGE_SIZE)
            records += found.records
            page++
        } while (page < found.totalPages && page < MAX_PAGES)
        return records
    }

    private fun settled(
        operator: String,
        order: String,
        callbacks: Int,
        within: Duration,
        every: Duration,
    ) {
        val deadline = Instant.now().plus(within)
        while (true) {
            val payment = control().payment(operator, order)
            if (payment != null && payment.settled && payment.callbacksSent >= callbacks) return
            if (Instant.now().plus(every).isAfter(deadline)) {
                throw PreparationException("the simulator did not settle the $operator payment for order $order${if (callbacks > 0) " with $callbacks callback(s)" else ""} within ${within.toSeconds()}s")
            }
            Thread.sleep(every)
        }
    }

    private fun paymentOf(
        operator: String,
        order: String,
    ): String = control().payment(operator, order)?.paymentId ?: throw PreparationException("the simulator holds no $operator payment for order $order")

    private fun control(): SimulatorControl = control ?: throw PreparationException("the scenario drives the simulator but no simulator control URL is configured")

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 50
    }
}
