package org.openfsp.simulator

import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.common.SimulationSettings
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
import org.openfsp.simulator.space.SimulationSpaces
import org.openfsp.simulator.space.SpaceId
import org.openfsp.simulator.space.SpaceSettings
import org.openfsp.simulator.space.spi.SpaceCleanup
import org.openfsp.simulator.space.spi.stubs.InMemorySpaceRegistry
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimulationSpacesTest {
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
        OperatorProfile("mock_openfsp", Dialect.OPENFSP, "MO", lookup = true, callbacks = true, idempotentByOrder = true, statements = true, returnUrl = null)
    private val profiles = OperatorProfiles { id -> openFsp.takeIf { it.id == id } }
    private val payments = InMemorySimulatedPayments()
    private val scenarios = InMemoryScenarioBook()
    private val journal = InMemoryRequestJournal()
    private val statements = InMemoryStatementBook()
    private val sequences = InMemorySequences()
    private val cleared = mutableListOf<SpaceId>()
    private val purged = mutableListOf<Pair<SpaceId, Instant>>()
    private val cleanup =
        object : SpaceCleanup {
            override fun clear(space: SpaceId) {
                cleared += space
            }

            override fun purge(
                space: SpaceId,
                before: Instant,
            ) {
                purged += space to before
            }
        }
    private val settings = SimulationSettings(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO)
    private val settlement = OperatorSettlement(payments, profiles, statements, { _, _, _ -> true }, { _, task -> task() }, clock, settings)
    private val simulation =
        OperatorSimulation(profiles, payments, scenarios, statements, settlement, sequences, { "pay_${UUID.randomUUID()}" }, { space -> "https://sim.example/runs/$space" }, {}, clock, settings)
    private val control = SimulationControl(payments, scenarios, journal, statements, settlement, sequences, listOf(cleanup), clock, profiles)
    private val spaces = SimulationSpaces(InMemorySpaceRegistry(), control, sequences, clock, SpaceSettings(Duration.ofHours(2), 3))
    private val caller = Caller(authenticated = true, credential = "Bearer key")

    private fun create(
        space: SpaceId,
        orderId: String,
        msisdn: String? = "50934567890",
    ) = (simulation.create(space, "mock_openfsp", caller, CreatePaymentAtOperator(orderId, "100.00", msisdn, null, null)) as OperatorResponse.Answered<OperatorPayment>).body

    private fun statusOf(
        space: SpaceId,
        orderId: String,
    ) = (simulation.lookup(space, "mock_openfsp", caller, orderId) as? OperatorResponse.Answered<OperatorPayment>)?.body?.status

    private fun entry(path: String) = JournalEntry("mock_openfsp", time.now, "POST", path, "{}")

    @Test
    fun `space ids are random 128 bit hex and only that shape parses`() {
        val first = SpaceId.random()
        assertEquals(32, first.value.length)
        assertNotEquals(first, SpaceId.random())
        assertEquals(first, SpaceId.parse(first.value))
        assertNull(SpaceId.parse("default"))
        assertNull(SpaceId.parse("../" + first.value))
    }

    @Test
    fun `two spaces with the same reference keep their own scenario and payment`() {
        val a = spaces.open()!!.id
        val b = spaces.open()!!.id
        control.declare(a, "mock_openfsp", "order-1", Scenario.DECLINE)
        control.declare(b, "mock_openfsp", "order-1", Scenario.PENDING_FOREVER)

        val inA = create(a, "order-1")
        val inB = create(b, "order-1")

        assertNotEquals(inA.paymentId, inB.paymentId)
        assertEquals("FAILED", statusOf(a, "order-1"))
        assertEquals("PENDING", statusOf(b, "order-1"))
        assertNull(statusOf(SpaceId.DEFAULT, "order-1"))
        assertEquals(1, control.payments(a).size)
        assertEquals(1, control.payments(b).size)
    }

    @Test
    fun `defaults and automatic rotation stay in their space`() {
        val a = spaces.open()!!.id
        val b = spaces.open()!!.id
        control.declareDefault(a, "mock_openfsp", Scenario.INSUFFICIENT)
        control.rotate(b, "mock_openfsp", true)

        assertEquals(Scenario.INSUFFICIENT, control.defaultFor(a, "mock_openfsp"))
        assertEquals(Scenario.SUCCESS, control.defaultFor(b, "mock_openfsp"))
        assertTrue(control.rotates(b, "mock_openfsp"))
        assertFalse(control.rotates(a, "mock_openfsp"))
        assertFalse(control.rotates(SpaceId.DEFAULT, "mock_openfsp"))
    }

    @Test
    fun `a reset only clears its own space`() {
        val a = spaces.open()!!.id
        val b = spaces.open()!!.id
        create(a, "order-1")
        create(b, "order-1")
        control.record(a, entry("/mock_openfsp/v1/payments"))
        control.record(b, entry("/mock_openfsp/v1/payments"))
        control.closeDay(b, "mock_openfsp", clock.today())

        control.reset(a)

        assertTrue(control.payments(a).isEmpty())
        assertTrue(control.journal(a, null).isEmpty())
        assertEquals(1, control.payments(b).size)
        assertEquals(1, control.journal(b, "mock_openfsp").size)
        assertEquals(listOf(clock.today()), control.statementDays(b, "mock_openfsp").closed)
        assertEquals(listOf(a), cleared)
        create(a, "order-2")
        assertEquals(1, control.payments(a).size)
    }

    @Test
    fun `each space keeps its own request journal`() {
        val a = spaces.open()!!.id
        control.record(a, entry("/mock_openfsp/v1/payments"))
        control.record(SpaceId.DEFAULT, entry("/mock_openfsp/v1/payments/MO-1"))

        assertEquals(listOf("/mock_openfsp/v1/payments"), control.journal(a, "mock_openfsp").map { it.path })
        assertEquals(listOf("/mock_openfsp/v1/payments/MO-1"), control.journal(SpaceId.DEFAULT, null).map { it.path })
    }

    @Test
    fun `an idle space expires and is purged while a used one stays open`() {
        val idle = spaces.open()!!.id
        val used = spaces.open()!!.id
        create(idle, "order-1")
        control.record(idle, entry("/mock_openfsp/v1/payments"))

        time.now = time.now.plus(Duration.ofMinutes(90))
        assertNotNull(spaces.enter(used))
        time.now = time.now.plus(Duration.ofMinutes(31))

        assertEquals(listOf(idle), spaces.expireIdle())
        assertNull(spaces.enter(idle))
        assertNull(spaces.find(idle))
        assertTrue(control.payments(idle).isEmpty())
        assertTrue(control.journal(idle, null).isEmpty())
        assertEquals(listOf(idle), cleared)
        assertNotNull(spaces.find(used))
        assertEquals(time.now.minus(Duration.ofMinutes(31)).plus(Duration.ofHours(2)), spaces.find(used)!!.expiresAt(spaces.idleLifetime))
    }

    @Test
    fun `the default space never closes and the capacity bounds the open spaces`() {
        assertFalse(spaces.close(SpaceId.DEFAULT))
        assertNull(spaces.enter(SpaceId.DEFAULT))
        repeat(3) { assertNotNull(spaces.open()) }
        assertNull(spaces.open())
    }

    @Test
    fun `a hosted checkout link stays in the space of its payment`() {
        val a = spaces.open()!!.id
        val hosted = create(a, "order-1", msisdn = null)
        assertEquals("https://sim.example/runs/$a/mock_openfsp/checkout/${hosted.paymentId}", hosted.checkoutUrl)
    }

    @Test
    fun `the default space forgets payments and cleanup records older than the idle lifetime`() {
        create(SpaceId.DEFAULT, "order-old")
        time.now = time.now.plus(Duration.ofMinutes(90))
        create(SpaceId.DEFAULT, "order-recent")
        time.now = time.now.plus(Duration.ofMinutes(31))

        spaces.expireIdle()

        assertEquals(listOf("order-recent"), control.payments(SpaceId.DEFAULT).map { it.orderId })
        assertEquals(listOf(SpaceId.DEFAULT to time.now.minus(Duration.ofHours(2))), purged)
    }

    @Test
    fun `only a provider whose orders are its references accepts a scenario by reference`() {
        assertEquals(Declaration.DECLARED, control.declareByReference(SpaceId.DEFAULT, "mock_openfsp", "ref-1", Scenario.DECLINE))
        assertEquals(Declaration.UNKNOWN_OPERATOR, control.declareByReference(SpaceId.DEFAULT, "mock_unknown", "ref-1", Scenario.DECLINE))
    }

    @Test
    fun `unmatched statement entries are paged in booking order and need the statements scope`() {
        val space = spaces.open()!!.id
        control.addEntry(space, "mock_openfsp", clock.today(), "2.00".toBigDecimal(), clock.localNow().plusSeconds(2))
        control.addEntry(space, "mock_openfsp", clock.today(), "1.00".toBigDecimal(), clock.localNow().plusSeconds(1))

        val first = simulation.statementEntries(space, "mock_openfsp", caller, StatementMatch.UNMATCHED, 0, 1) as OperatorResponse.Answered<StatementEntries>
        val second = simulation.statementEntries(space, "mock_openfsp", caller, StatementMatch.UNMATCHED, 1, 1) as OperatorResponse.Answered<StatementEntries>
        val forbidden = simulation.statementEntries(space, "mock_openfsp", caller.copy(principal = Principal.SECONDARY), StatementMatch.UNMATCHED, 0, 1)

        assertEquals(listOf("1.00"), first.body.entries.map { it.amount.toPlainString() })
        assertEquals(1, first.body.nextOffset)
        assertEquals(listOf("2.00"), second.body.entries.map { it.amount.toPlainString() })
        assertNull(second.body.nextOffset)
        assertEquals(403, (forbidden as OperatorResponse.Refused).status)
    }
}
