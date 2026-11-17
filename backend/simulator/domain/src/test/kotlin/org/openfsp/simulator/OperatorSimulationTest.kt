package org.openfsp.simulator

import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.common.SimulationSettings
import org.openfsp.simulator.common.spi.stubs.InMemorySequences
import org.openfsp.simulator.operator.Caller
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.operator.spi.OperatorProfiles
import org.openfsp.simulator.payment.CreatePaymentAtOperator
import org.openfsp.simulator.payment.OperatorPayment
import org.openfsp.simulator.payment.OperatorSimulation
import org.openfsp.simulator.payment.Outcome
import org.openfsp.simulator.payment.spi.stubs.InMemorySimulatedPayments
import org.openfsp.simulator.scenario.Scenario
import org.openfsp.simulator.scenario.spi.stubs.InMemoryScenarioBook
import org.openfsp.simulator.settlement.OperatorCallback
import org.openfsp.simulator.settlement.OperatorSettlement
import org.openfsp.simulator.settlement.spi.CallbackSender
import org.openfsp.simulator.space.SpaceId
import org.openfsp.simulator.statement.spi.stubs.InMemoryStatementBook
import java.time.Clock
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OperatorSimulationTest {
    private val sent = mutableListOf<Pair<String, OperatorCallback>>()
    private val sender = CallbackSender { operator, _, callback -> sent.add(operator to callback) }
    private val space = SpaceId.DEFAULT
    private val openFsp =
        OperatorProfile("mock_openfsp", Dialect.OPENFSP, "MO", lookup = true, callbacks = true, idempotentByOrder = true, statements = true, returnUrl = "https://gw.example/return/mock_openfsp")
    private val monCash = OperatorProfile("mock_moncash", Dialect.MONCASH, "MC", lookup = true, callbacks = false, idempotentByOrder = false, statements = false, returnUrl = null)
    private val profiles = OperatorProfiles { listOf(openFsp, monCash).firstOrNull { profile -> profile.id == it } }
    private val payments = InMemorySimulatedPayments()
    private val scenarios = InMemoryScenarioBook()
    private val statements = InMemoryStatementBook()
    private val clock = SimulationClock(Clock.systemUTC())
    private val settings = SimulationSettings(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO)
    private val settlement = OperatorSettlement(payments, profiles, statements, sender, { _, task -> task() }, clock, settings)
    private val simulation = OperatorSimulation(profiles, payments, scenarios, statements, settlement, InMemorySequences(), { "pay_${UUID.randomUUID()}" }, { "https://sim.example" }, {}, clock, settings)
    private val caller = Caller(authenticated = true, credential = "Bearer key")

    private fun create(
        orderId: String,
        operator: String = "mock_openfsp",
        msisdn: String? = "50934567890",
        who: Caller = caller,
    ) = simulation.create(space, operator, who, CreatePaymentAtOperator(orderId, "1250.00", msisdn, null, "https://gw.example/callbacks"))

    private fun created(
        orderId: String,
        operator: String = "mock_openfsp",
        msisdn: String? = "50934567890",
    ) = (create(orderId, operator, msisdn) as OperatorResponse.Answered<OperatorPayment>).body

    private fun lookup(orderId: String) = (simulation.lookup(space, "mock_openfsp", caller, orderId) as OperatorResponse.Answered<OperatorPayment>).body

    @Test
    fun `a payment without a declared scenario succeeds and calls back once`() {
        assertIs<OperatorResponse.Answered<OperatorPayment>>(create("o1"))
        assertEquals("SUCCESSFUL", lookup("o1").status)
        assertEquals(listOf("mock_openfsp"), sent.map { it.first })
    }

    @Test
    fun `a native payment id comes from the opaque generator while other dialects keep their numbering`() {
        val native = listOf(created("ids-1"), created("ids-2")).map { it.paymentId }
        val monCash = created("ids-3", operator = "mock_moncash").paymentId

        assertTrue(native.all { it.startsWith("pay_") && !it.contains("MO") })
        assertNotEquals(native[0], native[1])
        assertEquals("MC-00000001", monCash)
    }

    @Test
    fun `an unauthenticated caller is refused`() {
        assertEquals(401, (create("o2", who = Caller(authenticated = false, credential = "Bearer wrong")) as OperatorResponse.Refused).status)
    }

    @Test
    fun `a duplicate callback scenario calls back three times`() {
        scenarios.declare(space, "mock_openfsp", "o3", Scenario.DUPLICATE_CALLBACK)
        create("o3")
        assertEquals(3, sent.size)
    }

    @Test
    fun `a contradicting operator reports success then failure`() {
        scenarios.declare(space, "mock_openfsp", "o4", Scenario.CONTRADICT)
        create("o4")
        assertEquals("SUCCESSFUL", lookup("o4").status)
        assertEquals("FAILED", lookup("o4").status)
    }

    @Test
    fun `a payer approval needs no checkout, a payment without payer is paid on the hosted page`() {
        assertNull(created("o5").checkoutUrl)
        scenarios.declare(space, "mock_openfsp", "o6", Scenario.PENDING_FOREVER)
        val hosted = created("o6", msisdn = null)
        assertEquals("https://sim.example/mock_openfsp/checkout/${hosted.paymentId}", hosted.checkoutUrl)
        assertEquals(Outcome.SUCCESSFUL, simulation.pay(space, "mock_openfsp", hosted.paymentId, approved = true)?.outcome)
        assertEquals(2, statements.lines(space, "mock_openfsp", clock.localNow().toLocalDate()).size)
    }

    @Test
    fun `a MonCash payment is always paid on the hosted page and found by its transaction`() {
        val payment = created("o7", operator = "mock_moncash")
        assertNotNull(payment.checkoutUrl)
        val found = simulation.lookupPayment(space, "mock_moncash", caller, payment.paymentId) as OperatorResponse.Answered<OperatorPayment>
        assertEquals("o7", found.body.orderId)
        assertEquals(0, sent.count { it.first == "mock_moncash" })
    }

    @Test
    fun `a test profile offers only the scenarios its capability flags can produce`() {
        val statementOnly = OperatorProfile("mock_statement_only", Dialect.OPENFSP, "MT", lookup = false, callbacks = false, idempotentByOrder = false, statements = true, returnUrl = null)
        assertEquals(Scenario.entries.toSet() - setOf(Scenario.DUPLICATE_CALLBACK, Scenario.LATE_CALLBACK, Scenario.UNKNOWN_STATUS, Scenario.CONTRADICT), statementOnly.scenarios)
        assertEquals(Scenario.entries.toSet(), openFsp.scenarios)
        assertFalse(Scenario.LATE_CALLBACK in statementOnly.rotation)
    }
}
