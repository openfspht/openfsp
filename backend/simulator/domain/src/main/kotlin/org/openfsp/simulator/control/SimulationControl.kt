package org.openfsp.simulator.control

import org.openfsp.ddd.DomainService
import org.openfsp.simulator.common.spi.SimulationCleanup
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
import java.time.Instant

@DomainService
class SimulationControl(
    private val payments: SimulatedPayments,
    private val scenarios: ScenarioBook,
    private val journal: RequestJournal,
    private val settlement: OperatorSettlement,
    private val cleanups: List<SimulationCleanup>,
    private val profiles: OperatorProfiles,
) : ControlSimulation {
    override fun declare(
        operator: String,
        reference: String,
        scenario: Scenario,
    ): Boolean = supports(operator, scenario).also { if (it) scenarios.declare(operator, reference, scenario) }

    override fun declareByReference(
        operator: String,
        reference: String,
        scenario: Scenario,
    ): Declaration =
        when {
            profiles[operator]?.declaresByReference != true -> Declaration.UNKNOWN_OPERATOR
            !declare(operator, reference, scenario) -> Declaration.UNSUPPORTED
            else -> Declaration.DECLARED
        }

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
    ): Boolean = profiles[operator]?.scenarios?.contains(scenario) == true

    override fun defaultFor(operator: String): Scenario = scenarios.defaultFor(operator)

    override fun resolve(
        paymentId: String,
        outcome: Outcome,
        reason: String?,
    ): SimulatedPayment? = settlement.resolve(paymentId, outcome, reason)

    override fun deliverCallback(paymentId: String): SimulatedPayment? {
        payments.find(paymentId) ?: return null
        settlement.callBack(paymentId)
        return payments.find(paymentId)
    }

    override fun payments(): List<SimulatedPayment> = payments.all()

    override fun paymentByReference(
        operator: String,
        reference: String,
    ): SimulatedPayment? = payments.withReference(operator, reference)

    override fun journal(operator: String?) = journal.entries(operator)

    override fun record(entry: JournalEntry) = journal.record(entry)

    override fun reset() {
        payments.clear()
        scenarios.clear()
        journal.clear()
        cleanups.forEach { it.clear() }
    }

    override fun purge(before: Instant) {
        payments.purge(before)
        cleanups.forEach { it.purge(before) }
    }
}
