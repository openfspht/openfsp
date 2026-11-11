package org.openfsp.simulator.operator.control.api

import org.openfsp.simulator.operator.journal.JournalEntry
import org.openfsp.simulator.operator.payment.Outcome
import org.openfsp.simulator.operator.payment.SimulatedPayment
import org.openfsp.simulator.operator.scenario.Scenario
import org.openfsp.simulator.operator.statement.StatementDays
import org.openfsp.simulator.operator.statement.StatementLine
import java.time.LocalDate

interface ControlSimulation {
    fun declare(
        operator: String,
        orderId: String,
        scenario: Scenario,
    ): Boolean

    fun declareDefault(
        operator: String,
        scenario: Scenario,
    ): Boolean

    fun scenariosFor(operator: String): List<Scenario>

    fun rotate(
        operator: String,
        enabled: Boolean,
    ): Boolean

    fun rotates(operator: String): Boolean

    fun defaultFor(operator: String): Scenario

    fun resolve(
        paymentId: String,
        outcome: Outcome,
        reason: String?,
    ): SimulatedPayment?

    fun deliverCallback(paymentId: String): SimulatedPayment?

    fun closeDay(
        operator: String,
        day: LocalDate,
    )

    fun statementDays(operator: String): StatementDays

    fun addEntry(
        operator: String,
        day: LocalDate,
        amount: String,
        bookedAt: String,
    )

    fun payments(): List<SimulatedPayment>

    fun journal(operator: String?): List<JournalEntry>

    fun record(entry: JournalEntry)

    fun reset()

    fun statementLines(
        operator: String,
        day: LocalDate,
    ): List<StatementLine>
}
