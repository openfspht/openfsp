package org.openfsp.simulator.control

import org.openfsp.ddd.DomainService
import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.common.spi.Sequences
import org.openfsp.simulator.control.api.ControlSimulation
import org.openfsp.simulator.journal.JournalEntry
import org.openfsp.simulator.journal.spi.RequestJournal
import org.openfsp.simulator.operator.spi.OperatorProfiles
import org.openfsp.simulator.payment.Outcome
import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.payment.spi.SimulatedPayments
import org.openfsp.simulator.scenario.Scenario
import org.openfsp.simulator.scenario.spi.ScenarioBook
import org.openfsp.simulator.settlement.OperatorSettlement
import org.openfsp.simulator.space.SpaceId
import org.openfsp.simulator.space.spi.SpaceCleanup
import org.openfsp.simulator.statement.StatementDays
import org.openfsp.simulator.statement.StatementLine
import org.openfsp.simulator.statement.spi.StatementBook
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

@DomainService
class SimulationControl(
    private val payments: SimulatedPayments,
    private val scenarios: ScenarioBook,
    private val journal: RequestJournal,
    private val statements: StatementBook,
    private val settlement: OperatorSettlement,
    private val sequences: Sequences,
    private val cleanups: List<SpaceCleanup>,
    private val clock: SimulationClock,
    private val profiles: OperatorProfiles,
) : ControlSimulation {
    override fun declare(
        space: SpaceId,
        operator: String,
        orderId: String,
        scenario: Scenario,
    ): Boolean = supports(operator, scenario).also { if (it) scenarios.declare(space, operator, orderId, scenario) }

    override fun declareByReference(
        space: SpaceId,
        operator: String,
        reference: String,
        scenario: Scenario,
    ): Declaration =
        when {
            profiles[operator]?.declaresByReference != true -> Declaration.UNKNOWN_OPERATOR
            !declare(space, operator, reference, scenario) -> Declaration.UNSUPPORTED
            else -> Declaration.DECLARED
        }

    override fun declareDefault(
        space: SpaceId,
        operator: String,
        scenario: Scenario,
    ): Boolean = supports(operator, scenario).also { if (it) scenarios.declareDefault(space, operator, scenario) }

    override fun rotate(
        space: SpaceId,
        operator: String,
        enabled: Boolean,
    ): Boolean = (profiles[operator] != null).also { if (it) scenarios.rotate(space, operator, enabled) }

    override fun rotates(
        space: SpaceId,
        operator: String,
    ): Boolean = scenarios.rotates(space, operator)

    override fun scenariosFor(operator: String): List<Scenario> = Scenario.entries.filter { supports(operator, it) }

    private fun supports(
        operator: String,
        scenario: Scenario,
    ): Boolean = profiles[operator]?.scenarios?.contains(scenario) == true

    override fun defaultFor(
        space: SpaceId,
        operator: String,
    ): Scenario = scenarios.defaultFor(space, operator)

    override fun resolve(
        space: SpaceId,
        paymentId: String,
        outcome: Outcome,
        reason: String?,
    ): SimulatedPayment? = settlement.resolve(space, paymentId, outcome, reason)

    override fun deliverCallback(
        space: SpaceId,
        paymentId: String,
    ): SimulatedPayment? {
        payments.find(space, paymentId) ?: return null
        settlement.callBack(space, paymentId)
        return payments.find(space, paymentId)
    }

    override fun closeDay(
        space: SpaceId,
        operator: String,
        day: LocalDate,
    ) = statements.close(space, operator, day)

    override fun statementDays(
        space: SpaceId,
        operator: String,
    ) = StatementDays(clock.today(), statements.closedDays(space, operator))

    override fun addEntry(
        space: SpaceId,
        operator: String,
        day: LocalDate,
        amount: BigDecimal,
        bookedAt: LocalDateTime,
    ) = statements.book(space, operator, day, StatementLine("XX-%08d".format(sequences.next(space, UNMATCHED)), null, "PAYMENT", amount, bookedAt))

    override fun payments(space: SpaceId): List<SimulatedPayment> = payments.all(space)

    override fun journal(
        space: SpaceId,
        operator: String?,
    ) = journal.entries(space, operator)

    override fun record(
        space: SpaceId,
        entry: JournalEntry,
    ) = journal.record(space, entry)

    override fun reset(space: SpaceId) {
        payments.clear(space)
        scenarios.clear(space)
        journal.clear(space)
        statements.clear(space)
        cleanups.forEach { it.clear(space) }
    }

    override fun purge(
        space: SpaceId,
        before: Instant,
    ) {
        payments.purge(space, before)
        cleanups.forEach { it.purge(space, before) }
    }

    private companion object {
        const val UNMATCHED = "unmatched"
    }
}
