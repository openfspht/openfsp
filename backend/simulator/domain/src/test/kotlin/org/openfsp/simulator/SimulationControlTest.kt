package org.openfsp.simulator

import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.common.SimulationSettings
import org.openfsp.simulator.common.spi.SimulationCleanup
import org.openfsp.simulator.common.spi.stubs.InMemorySequences
import org.openfsp.simulator.control.Declaration
import org.openfsp.simulator.control.SimulationControl
import org.openfsp.simulator.journal.JournalEntry
import org.openfsp.simulator.journal.spi.stubs.InMemoryRequestJournal
import org.openfsp.simulator.operator.Caller
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.operator.Principal
import org.openfsp.simulator.operator.spi.OperatorProfiles
import org.openfsp.simulator.payment.CreatePaymentAtOperator
import org.openfsp.simulator.payment.OperatorPayment
import org.openfsp.simulator.payment.OperatorSimulation
import org.openfsp.simulator.payment.spi.stubs.InMemorySimulatedPayments
import org.openfsp.simulator.scenario.Scenario
import org.openfsp.simulator.scenario.spi.stubs.InMemoryScenarioBook
import org.openfsp.simulator.settlement.OperatorSettlement
import org.openfsp.simulator.statement.StatementEntries
import org.openfsp.simulator.statement.StatementMatch
import org.openfsp.simulator.statement.spi.stubs.InMemoryStatementBook
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
    private val statements = InMemoryStatementBook()
    private val sequences = InMemorySequences()
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
    private val settlement = OperatorSettlement(payments, profiles, statements, { _, _, _ -> true }, { _, task -> task() }, clock, settings)
    private val simulation =
        OperatorSimulation(profiles, payments, scenarios, statements, settlement, sequences, { "pay_${UUID.randomUUID()}" }, { "https://sim.example" }, {}, clock, settings)
    private val control = SimulationControl(payments, scenarios, journal, statements, settlement, sequences, listOf(cleanup), clock, profiles)
    private val caller = Caller(authenticated = true, credential = "Bearer key")

    private fun create(reference: String) =
        (simulation.create("mock_openfsp", caller, CreatePaymentAtOperator(reference, "100.00", "50934567890", null, null)) as OperatorResponse.Answered<OperatorPayment>).body

    private fun entry(path: String) = JournalEntry("mock_openfsp", time.now, "POST", path, "{}")

    @Test
    fun `a reset clears payments, scenarios, the journal, statements and cleanup records`() {
        control.declareDefault("mock_openfsp", Scenario.INSUFFICIENT)
        control.rotate("mock_openfsp", true)
        create("order-1")
        control.record(entry("/mock_openfsp/v1/payments"))
        control.closeDay("mock_openfsp", clock.today())

        control.reset()

        assertTrue(control.payments().isEmpty())
        assertTrue(control.journal(null).isEmpty())
        assertTrue(control.statementDays("mock_openfsp").closed.isEmpty())
        assertEquals(Scenario.SUCCESS, control.defaultFor("mock_openfsp"))
        assertFalse(control.rotates("mock_openfsp"))
        assertEquals(1, cleared)
        create("order-1")
        assertEquals(1, control.payments().size)
    }

    @Test
    fun `the journal is filtered by operator`() {
        control.record(entry("/mock_openfsp/v1/payments"))

        assertEquals(listOf("/mock_openfsp/v1/payments"), control.journal("mock_openfsp").map { it.path })
        assertTrue(control.journal("mock_moncash").isEmpty())
    }

    @Test
    fun `a purge forgets payments and cleanup records older than its horizon`() {
        create("order-old")
        time.now = time.now.plus(Duration.ofMinutes(90))
        create("order-recent")
        time.now = time.now.plus(Duration.ofMinutes(31))
        val horizon = time.now.minus(Duration.ofHours(2))

        control.purge(horizon)

        assertEquals(listOf("order-recent"), control.payments().map { it.reference })
        assertEquals(listOf(horizon), purged)
        assertEquals(0, cleared)
    }

    @Test
    fun `only a provider whose orders are its references accepts a scenario by reference`() {
        assertEquals(Declaration.DECLARED, control.declareByReference("mock_openfsp", "ref-1", Scenario.DECLINE))
        assertEquals(Declaration.UNKNOWN_OPERATOR, control.declareByReference("mock_unknown", "ref-1", Scenario.DECLINE))
    }

    @Test
    fun `unmatched statement entries are paged in booking order and need the statements scope`() {
        control.addEntry("mock_openfsp", clock.today(), "2.00".toBigDecimal(), clock.localNow().plusSeconds(2))
        control.addEntry("mock_openfsp", clock.today(), "1.00".toBigDecimal(), clock.localNow().plusSeconds(1))

        val first = simulation.statementEntries("mock_openfsp", caller, StatementMatch.UNMATCHED, 0, 1) as OperatorResponse.Answered<StatementEntries>
        val second = simulation.statementEntries("mock_openfsp", caller, StatementMatch.UNMATCHED, 1, 1) as OperatorResponse.Answered<StatementEntries>
        val forbidden = simulation.statementEntries("mock_openfsp", caller.copy(principal = Principal.SECONDARY), StatementMatch.UNMATCHED, 0, 1)

        assertEquals(listOf("1.00"), first.body.entries.map { it.amount.toPlainString() })
        assertEquals(1, first.body.nextOffset)
        assertEquals(listOf("2.00"), second.body.entries.map { it.amount.toPlainString() })
        assertNull(second.body.nextOffset)
        assertEquals(403, (forbidden as OperatorResponse.Refused).status)
    }
}
