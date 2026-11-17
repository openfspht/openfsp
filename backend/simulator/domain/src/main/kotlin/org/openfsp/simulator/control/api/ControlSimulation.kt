package org.openfsp.simulator.control.api

import org.openfsp.simulator.control.Declaration
import org.openfsp.simulator.journal.JournalEntry
import org.openfsp.simulator.payment.Outcome
import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.scenario.Scenario
import org.openfsp.simulator.space.SpaceId
import org.openfsp.simulator.statement.StatementDays
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

interface ControlSimulation {
    fun declare(
        space: SpaceId,
        operator: String,
        reference: String,
        scenario: Scenario,
    ): Boolean

    fun declareByReference(
        space: SpaceId,
        operator: String,
        reference: String,
        scenario: Scenario,
    ): Declaration

    fun declareDefault(
        space: SpaceId,
        operator: String,
        scenario: Scenario,
    ): Boolean

    fun scenariosFor(operator: String): List<Scenario>

    fun rotate(
        space: SpaceId,
        operator: String,
        enabled: Boolean,
    ): Boolean

    fun rotates(
        space: SpaceId,
        operator: String,
    ): Boolean

    fun defaultFor(
        space: SpaceId,
        operator: String,
    ): Scenario

    fun resolve(
        space: SpaceId,
        paymentId: String,
        outcome: Outcome,
        reason: String?,
    ): SimulatedPayment?

    fun deliverCallback(
        space: SpaceId,
        paymentId: String,
    ): SimulatedPayment?

    fun closeDay(
        space: SpaceId,
        operator: String,
        day: LocalDate,
    )

    fun statementDays(
        space: SpaceId,
        operator: String,
    ): StatementDays

    fun addEntry(
        space: SpaceId,
        operator: String,
        day: LocalDate,
        amount: BigDecimal,
        bookedAt: LocalDateTime,
    )

    fun payments(space: SpaceId): List<SimulatedPayment>

    fun journal(
        space: SpaceId,
        operator: String?,
    ): List<JournalEntry>

    fun record(
        space: SpaceId,
        entry: JournalEntry,
    )

    fun reset(space: SpaceId)

    fun purge(
        space: SpaceId,
        before: Instant,
    )
}
