package org.openfsp.simulator.operator.spi

import org.openfsp.simulator.operator.JournalEntry
import org.openfsp.simulator.operator.OperatorCallback
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.Scenario
import org.openfsp.simulator.operator.SimulatedPayment
import org.openfsp.simulator.operator.StatementLine
import java.time.Duration
import java.time.LocalDate

fun interface OperatorProfiles {
    operator fun get(id: String): OperatorProfile?
}

interface SimulatedPayments {
    fun save(payment: SimulatedPayment): SimulatedPayment

    fun find(paymentId: String): SimulatedPayment?

    fun withOrder(
        operator: String,
        orderId: String,
    ): SimulatedPayment?

    fun update(
        paymentId: String,
        change: (SimulatedPayment) -> SimulatedPayment,
    ): SimulatedPayment?

    fun forget(paymentId: String)

    fun all(): List<SimulatedPayment>

    fun clear()
}

interface ScenarioBook {
    fun declare(
        operator: String,
        orderId: String,
        scenario: Scenario,
    )

    fun declareDefault(
        operator: String,
        scenario: Scenario,
    )

    fun defaultFor(operator: String): Scenario

    fun scenarioFor(
        operator: String,
        orderId: String,
    ): Scenario

    fun clear()
}

interface RequestJournal {
    fun record(entry: JournalEntry)

    fun entries(operator: String?): List<JournalEntry>

    fun clear()
}

interface StatementBook {
    fun book(
        operator: String,
        day: LocalDate,
        line: StatementLine,
    )

    fun close(
        operator: String,
        day: LocalDate,
    )

    fun isClosed(
        operator: String,
        day: LocalDate,
    ): Boolean

    fun lines(
        operator: String,
        day: LocalDate,
    ): List<StatementLine>

    fun clear()
}

fun interface CallbackSender {
    fun send(
        url: String,
        callback: OperatorCallback,
        secret: String?,
    ): Boolean
}

fun interface Timers {
    fun after(
        delay: Duration,
        task: () -> Unit,
    )
}

fun interface Delays {
    fun pause(duration: Duration)
}
