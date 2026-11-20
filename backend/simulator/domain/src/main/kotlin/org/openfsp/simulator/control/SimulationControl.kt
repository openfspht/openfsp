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
        account: String,
        reference: String,
        scenario: Scenario,
    ): Boolean = supports(operator, scenario).also { if (it) scenarios.declare(operator, account, reference, scenario) }

    override fun declareDefault(
        operator: String,
        account: String,
        scenario: Scenario,
    ): Boolean = supports(operator, scenario).also { if (it) scenarios.declareDefault(operator, account, scenario) }

    override fun rotate(
        operator: String,
        account: String,
        enabled: Boolean,
    ): Boolean = (profiles[operator] != null).also { if (it) scenarios.rotate(operator, account, enabled) }

    override fun rotates(
        operator: String,
        account: String,
    ): Boolean = scenarios.rotates(operator, account)

    override fun scenariosFor(operator: String): List<Scenario> = Scenario.entries.filter { supports(operator, it) }

    private fun supports(
        operator: String,
        scenario: Scenario,
    ): Boolean = profiles[operator]?.scenarios?.contains(scenario) == true

    override fun defaultFor(
        operator: String,
        account: String,
    ): Scenario = scenarios.defaultFor(operator, account)

    override fun resolve(
        scope: ControlScope,
        paymentId: String,
        outcome: Outcome,
        reason: String?,
    ): SimulatedPayment? = visible(scope, paymentId)?.let { settlement.resolve(it.paymentId, outcome, reason) }

    override fun deliverCallback(
        scope: ControlScope,
        paymentId: String,
    ): SimulatedPayment? {
        visible(scope, paymentId) ?: return null
        settlement.callBack(paymentId)
        return payments.find(paymentId)
    }

    override fun payments(scope: ControlScope): List<SimulatedPayment> = payments.all().filter { scope.sees(it.account) }

    override fun paymentByReference(
        operator: String,
        reference: String,
        account: String,
    ): SimulatedPayment? = payments.withReference(operator, reference, account)

    override fun journal(
        scope: ControlScope,
        operator: String?,
    ) = journal.entries(operator).filter { scope.sees(it.account) }

    override fun record(entry: JournalEntry) = journal.record(entry)

    override fun reset(scope: ControlScope) {
        when (scope) {
            ControlScope.Instance -> {
                payments.clear()
                scenarios.clear()
                journal.clear()
                cleanups.forEach { it.clear() }
            }
            is ControlScope.MerchantAccount -> {
                payments.all().filter { it.account == scope.account }.forEach { payments.forget(it.paymentId) }
                scenarios.clear(scope.account)
                journal.clear(scope.account)
                cleanups.forEach { it.clear(scope.account) }
            }
        }
    }

    private fun visible(
        scope: ControlScope,
        paymentId: String,
    ): SimulatedPayment? = payments.find(paymentId)?.takeIf { scope.sees(it.account) }

    override fun purge(before: Instant) {
        payments.purge(before)
        cleanups.forEach { it.purge(before) }
    }
}
