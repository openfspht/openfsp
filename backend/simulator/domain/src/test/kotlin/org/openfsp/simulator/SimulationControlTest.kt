package org.openfsp.simulator

import org.openfsp.simulator.control.ControlScope
import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.common.SimulationSettings
import org.openfsp.simulator.common.spi.SimulationCleanup
import org.openfsp.simulator.common.spi.stubs.InMemorySequences
import org.openfsp.simulator.control.Declaration
import org.openfsp.simulator.control.SimulationControl
import org.openfsp.simulator.journal.JournalEntry
import org.openfsp.simulator.journal.spi.stubs.InMemoryRequestJournal
import org.openfsp.simulator.operator.Accounts
import org.openfsp.simulator.operator.Caller
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.operator.Principal
import org.openfsp.simulator.operator.spi.OperatorProfiles
import org.openfsp.simulator.payment.CreatePaymentAtOperator
import org.openfsp.simulator.payment.OperatorPayment
import org.openfsp.simulator.payment.OperatorSimulation
import org.openfsp.simulator.payment.Outcome
import org.openfsp.simulator.payment.StatementPage
import org.openfsp.simulator.payment.spi.stubs.InMemorySimulatedPayments
import org.openfsp.simulator.scenario.Scenario
import org.openfsp.simulator.scenario.spi.stubs.InMemoryScenarioBook
import org.openfsp.simulator.settlement.OperatorSettlement
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimulationControlTest {
    private class MovableClock(
        var now: Instant = Instant.parse("2026-11-11T15:00:00Z"),
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }

    private val time = MovableClock()
    private val clock = SimulationClock(time)
    private val openFsp =
        OperatorProfile("mock_openfsp", Dialect.OPENFSP, "MO", lookup = true, callbacks = true, idempotentByReference = true, statements = true, returnUrl = null)
    private val profiles = OperatorProfiles { id -> openFsp.takeIf { it.id == id } }
    private val payments = InMemorySimulatedPayments()
    private val scenarios = InMemoryScenarioBook()
    private val journal = InMemoryRequestJournal()
    private var cleared = 0
    private val purged = mutableListOf<Instant>()
    private val cleanup =
        object : SimulationCleanup {
            override fun clear() {
                cleared++
            }

            override fun purge(before: Instant) {
                purged += before
            }
        }
    private val settings = SimulationSettings(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO)
    private val settlement = OperatorSettlement(payments, profiles, { _, _, _ -> true }, { _, task -> task() }, clock, settings, {})
    private val simulation =
        OperatorSimulation(profiles, payments, scenarios, settlement, InMemorySequences(), { "pay_${UUID.randomUUID()}" }, { "https://sim.example" }, {}, clock, settings, {})
    private val control = SimulationControl(payments, scenarios, journal, settlement, listOf(cleanup), profiles)
    private val caller = Caller.ofPublishedAccount(authenticated = true, credential = "Bearer key")

    private fun create(reference: String) =
        (simulation.create("mock_openfsp", caller, CreatePaymentAtOperator(reference, "100.00", "50934567890", null, null)) as OperatorResponse.Answered<OperatorPayment>).body

    private fun entry(path: String) = JournalEntry("mock_openfsp", time.now, "POST", path, "{}")

    @Test
    fun `a reset clears payments, scenarios, the journal and cleanup records`() {
        control.declareDefault("mock_openfsp", Scenario.INSUFFICIENT)
        control.rotate("mock_openfsp", true)
        create("order-1")
        control.record(entry("/mock_openfsp/v1/payments"))

        control.reset(ControlScope.Instance)

        assertTrue(control.payments(ControlScope.Instance).isEmpty())
        assertTrue(control.journal(ControlScope.Instance, null).isEmpty())
        assertEquals(Scenario.SUCCESS, control.defaultFor("mock_openfsp"))
        assertFalse(control.rotates("mock_openfsp"))
        assertEquals(1, cleared)
        create("order-1")
        assertEquals(1, control.payments(ControlScope.Instance).size)
    }

    @Test
    fun `the journal is filtered by operator`() {
        control.record(entry("/mock_openfsp/v1/payments"))

        assertEquals(listOf("/mock_openfsp/v1/payments"), control.journal(ControlScope.Instance, "mock_openfsp").map { it.path })
        assertTrue(control.journal(ControlScope.Instance, "mock_moncash").isEmpty())
    }

    @Test
    fun `a purge forgets payments and cleanup records older than its horizon`() {
        create("order-old")
        time.now = time.now.plus(Duration.ofMinutes(90))
        create("order-recent")
        time.now = time.now.plus(Duration.ofMinutes(31))
        val horizon = time.now.minus(Duration.ofHours(2))

        control.purge(horizon)

        assertEquals(listOf("order-recent"), control.payments(ControlScope.Instance).map { it.reference })
        assertEquals(listOf(horizon), purged)
        assertEquals(0, cleared)
    }

    @Test
    fun `a merchant account scope sees, settles and resets only its own payments and journal`() {
        val other = Caller(authenticated = true, credential = "Bearer other", principal = Principal.PRIMARY, account = "run_other")
        val mine = create("order-mine")
        (simulation.create("mock_openfsp", other, CreatePaymentAtOperator("order-theirs", "100.00", null, null, null)) as OperatorResponse.Answered<OperatorPayment>).body
        control.record(entry("/mock_openfsp/v1/payments").copy(account = Accounts.PUBLISHED))
        control.record(entry("/mock_openfsp/v1/payments").copy(account = "run_other"))
        control.record(entry("/mock_openfsp/conformance"))
        val scope = ControlScope.MerchantAccount("run_other")

        assertEquals(listOf("order-theirs"), control.payments(scope).map { it.reference })
        assertEquals(1, control.journal(scope, null).size)
        assertNull(control.resolve(scope, mine.paymentId, Outcome.FAILED, null))
        assertNull(control.deliverCallback(scope, mine.paymentId))

        control.reset(scope)

        assertEquals(listOf("order-mine"), control.payments(ControlScope.Instance).map { it.reference })
        assertEquals(2, control.journal(ControlScope.Instance, null).size)
        assertEquals(0, cleared)
    }

    @Test
    fun `only a provider whose orders are its references accepts a scenario by reference`() {
        assertEquals(Declaration.DECLARED, control.declareByReference("mock_openfsp", "ref-1", Scenario.DECLINE, Accounts.PUBLISHED))
        assertEquals(Declaration.UNKNOWN_OPERATOR, control.declareByReference("mock_unknown", "ref-1", Scenario.DECLINE, Accounts.PUBLISHED))
    }

    @Test
    fun `the statement pages the succeeded payments of its days in completion order and needs the statements scope`() {
        scenarios.declare("mock_openfsp", Accounts.PUBLISHED, "order-declined", Scenario.DECLINE)
        create("order-1")
        time.now = time.now.plusSeconds(1)
        create("order-declined")
        create("order-2")
        val today = clock.today()

        val first = simulation.statement("mock_openfsp", caller, today, today, 0, 1) as OperatorResponse.Answered<StatementPage>
        val second = simulation.statement("mock_openfsp", caller, today.minusDays(1), today, 1, 1) as OperatorResponse.Answered<StatementPage>
        val before = simulation.statement("mock_openfsp", caller, today.minusDays(2), today.minusDays(1), 0, 1) as OperatorResponse.Answered<StatementPage>
        val forbidden = simulation.statement("mock_openfsp", caller.copy(principal = Principal.SECONDARY), today, today, 0, 1)

        assertEquals(listOf("order-1"), first.body.payments.map { it.reference })
        assertEquals(1, first.body.nextOffset)
        assertEquals(listOf("order-2"), second.body.payments.map { it.reference })
        assertNull(second.body.nextOffset)
        assertTrue(before.body.payments.isEmpty())
        assertEquals(403, (forbidden as OperatorResponse.Refused).status)
    }
}
