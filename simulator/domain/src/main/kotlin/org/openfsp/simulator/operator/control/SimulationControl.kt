package org.openfsp.simulator.operator.control

import org.openfsp.ddd.DomainService
import org.openfsp.simulator.operator.SimulationClock
import org.openfsp.simulator.operator.control.api.ControlSimulation
import org.openfsp.simulator.operator.journal.JournalEntry
import org.openfsp.simulator.operator.journal.spi.RequestJournal
import org.openfsp.simulator.operator.payment.Outcome
import org.openfsp.simulator.operator.payment.SimulatedPayment
import org.openfsp.simulator.operator.payment.spi.SimulatedPayments
import org.openfsp.simulator.operator.scenario.Scenario
import org.openfsp.simulator.operator.scenario.spi.ScenarioBook
import org.openfsp.simulator.operator.settlement.OperatorSettlement
import org.openfsp.simulator.operator.spi.OperatorProfiles
import org.openfsp.simulator.operator.statement.StatementDays
import org.openfsp.simulator.operator.statement.StatementLine
import org.openfsp.simulator.operator.statement.spi.StatementBook
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

@DomainService
class SimulationControl(
    private val payments: SimulatedPayments,
    private val scenarios: ScenarioBook,
    private val journal: RequestJournal,
    private val statements: StatementBook,
    private val settlement: OperatorSettlement,
    private val clock: SimulationClock,
    private val profiles: OperatorProfiles,
) : ControlSimulation {
    private val unmatched = AtomicLong()

    override fun declare(
        operator: String,
        orderId: String,
        scenario: Scenario,
    ): Boolean = supports(operator, scenario).also { if (it) scenarios.declare(operator, orderId, scenario) }

    override fun declareDefault(
        operator: String,
        scenario: Scenario,
    ): Boolean = supports(operator, scenario).also { if (it) scenarios.declareDefault(operator, scenario) }

    override fun rotate(
        operator: String,
        enabled: Boolean,
    ): Boolean = (profiles[operator] != null).also { if (it) scenarios.rotate(operator, enabled) }

    override fun rotates(operator: String): Boolean = scenarios.rotates(operator)

    override fun scenariosFor(operator: String): List<Scenario> = Scenario.entries.filter { supports(operator, it) }

    private fun supports(
        operator: String,
        scenario: Scenario,
    ): Boolean = profiles[operator]?.dialect?.scenarios?.contains(scenario) == true

    override fun defaultFor(operator: String): Scenario = scenarios.defaultFor(operator)

    override fun resolve(
        paymentId: String,
        outcome: Outcome,
        reason: String?,
    ): SimulatedPayment? = settlement.resolve(paymentId, outcome, reason)

    override fun deliverCallback(paymentId: String): SimulatedPayment? {
        val payment = payments.find(paymentId) ?: return null
        settlement.callBack(payment)
        return payments.find(paymentId)
    }

    override fun closeDay(
        operator: String,
        day: LocalDate,
    ) = statements.close(operator, day)

    override fun statementDays(operator: String) = StatementDays(clock.today(), statements.closedDays(operator))

    override fun addEntry(
        operator: String,
        day: LocalDate,
        amount: String,
        bookedAt: String,
    ) = statements.book(operator, day, StatementLine("XX-%08d".format(unmatched.incrementAndGet()), null, "PAYMENT", amount, bookedAt))

    override fun payments(): List<SimulatedPayment> = payments.all()

    override fun journal(operator: String?) = journal.entries(operator)

    override fun record(entry: JournalEntry) = journal.record(entry)

    override fun statementLines(
        operator: String,
        day: LocalDate,
    ) = statements.lines(operator, day)

    override fun reset() {
        payments.clear()
        scenarios.clear()
        journal.clear()
        statements.clear()
    }
}
