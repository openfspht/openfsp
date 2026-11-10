package org.openfsp.simulator.operator

import org.openfsp.ddd.DomainService
import org.openfsp.simulator.operator.api.ControlSimulation
import org.openfsp.simulator.operator.spi.RequestJournal
import org.openfsp.simulator.operator.spi.ScenarioBook
import org.openfsp.simulator.operator.spi.SimulatedPayments
import org.openfsp.simulator.operator.spi.StatementBook
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
) : ControlSimulation {
    private val unmatched = AtomicLong()

    override fun declare(
        operator: String,
        orderId: String,
        scenario: Scenario,
    ) = scenarios.declare(operator, orderId, scenario)

    override fun declareDefault(
        operator: String,
        scenario: Scenario,
    ) = scenarios.declareDefault(operator, scenario)

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
