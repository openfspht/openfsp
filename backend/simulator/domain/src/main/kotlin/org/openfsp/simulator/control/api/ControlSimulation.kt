package org.openfsp.simulator.control.api

import org.openfsp.simulator.control.Declaration
import org.openfsp.simulator.journal.JournalEntry
import org.openfsp.simulator.payment.Outcome
import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.scenario.Scenario
import org.openfsp.simulator.statement.StatementDays
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

interface ControlSimulation {
    fun declare(
        operator: String,
        reference: String,
        scenario: Scenario,
    ): Boolean

    fun declareByReference(
        operator: String,
        reference: String,
        scenario: Scenario,
    ): Declaration

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
        amount: BigDecimal,
        bookedAt: LocalDateTime,
    )

    fun payments(): List<SimulatedPayment>

    fun paymentByReference(
        operator: String,
        reference: String,
    ): SimulatedPayment?

    fun journal(operator: String?): List<JournalEntry>

    fun record(entry: JournalEntry)

    fun reset()

    fun purge(before: Instant)
}
