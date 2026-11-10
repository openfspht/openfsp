package org.openfsp.simulator

import org.openfsp.simulator.operator.Caller
import org.openfsp.simulator.operator.CreatePaymentAtOperator
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.OperatorCallback
import org.openfsp.simulator.operator.OperatorPayment
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.operator.OperatorSettlement
import org.openfsp.simulator.operator.OperatorSimulation
import org.openfsp.simulator.operator.Outcome
import org.openfsp.simulator.operator.Scenario
import org.openfsp.simulator.operator.SimulationClock
import org.openfsp.simulator.operator.SimulationSettings
import org.openfsp.simulator.operator.spi.CallbackSender
import org.openfsp.simulator.operator.spi.OperatorProfiles
import org.openfsp.simulator.operator.spi.stubs.InMemoryScenarioBook
import org.openfsp.simulator.operator.spi.stubs.InMemorySimulatedPayments
import org.openfsp.simulator.operator.spi.stubs.InMemoryStatementBook
import java.time.Clock
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OperatorSimulationTest {
    private val sent = mutableListOf<Pair<String, OperatorCallback>>()
    private val sender = CallbackSender { operator, _, callback -> sent.add(operator to callback) }
    private val openFsp =
        OperatorProfile("mock_openfsp", Dialect.OPENFSP, "MO", lookup = true, callbacks = true, idempotentByOrder = true, statements = true, returnUrl = "https://gw.example/return/mock_openfsp")
    private val monCash = OperatorProfile("mock_moncash", Dialect.MONCASH, "MC", lookup = true, callbacks = false, idempotentByOrder = false, statements = false, returnUrl = null)
    private val profiles = OperatorProfiles { listOf(openFsp, monCash).firstOrNull { profile -> profile.id == it } }
    private val payments = InMemorySimulatedPayments()
    private val scenarios = InMemoryScenarioBook()
    private val statements = InMemoryStatementBook()
    private val clock = SimulationClock(Clock.systemUTC())
    private val settings = SimulationSettings(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, "https://sim.example")
    private val settlement = OperatorSettlement(payments, profiles, statements, sender, { _, task -> task() }, clock, settings)
    private val simulation = OperatorSimulation(profiles, payments, scenarios, statements, settlement, {}, clock, settings)
    private val caller = Caller(authenticated = true, credential = "Bearer key")

    private fun create(
        orderId: String,
        operator: String = "mock_openfsp",
        msisdn: String? = "50934567890",
        who: Caller = caller,
    ) = simulation.create(operator, who, CreatePaymentAtOperator(orderId, "1250.00", msisdn, null, "https://gw.example/callbacks"))

    private fun created(
        orderId: String,
        operator: String = "mock_openfsp",
        msisdn: String? = "50934567890",
    ) = (create(orderId, operator, msisdn) as OperatorResponse.Answered<OperatorPayment>).body

    private fun lookup(orderId: String) = (simulation.lookup("mock_openfsp", caller, orderId) as OperatorResponse.Answered<OperatorPayment>).body

    @Test
    fun `a payment without a declared scenario succeeds and calls back once`() {
        assertIs<OperatorResponse.Answered<OperatorPayment>>(create("o1"))
        assertEquals("SUCCESSFUL", lookup("o1").status)
        assertEquals(listOf("mock_openfsp"), sent.map { it.first })
    }

    @Test
    fun `an unauthenticated caller is refused`() {
        assertEquals(401, (create("o2", who = Caller(authenticated = false, credential = "Bearer wrong")) as OperatorResponse.Refused).status)
    }

    @Test
    fun `a duplicate callback scenario calls back three times`() {
        scenarios.declare("mock_openfsp", "o3", Scenario.DUPLICATE_CALLBACK)
        create("o3")
        assertEquals(3, sent.size)
    }

    @Test
    fun `a contradicting operator reports success then failure`() {
        scenarios.declare("mock_openfsp", "o4", Scenario.CONTRADICT)
        create("o4")
        assertEquals("SUCCESSFUL", lookup("o4").status)
        assertEquals("FAILED", lookup("o4").status)
    }

    @Test
    fun `a payer approval needs no checkout, a payment without payer is paid on the hosted page`() {
        assertNull(created("o5").checkoutUrl)
        scenarios.declare("mock_openfsp", "o6", Scenario.PENDING_FOREVER)
        val hosted = created("o6", msisdn = null)
        assertEquals("https://sim.example/mock_openfsp/checkout/${hosted.paymentId}", hosted.checkoutUrl)
        assertEquals(Outcome.SUCCESSFUL, simulation.pay("mock_openfsp", hosted.paymentId, approved = true)?.outcome)
        assertEquals(2, statements.lines("mock_openfsp", clock.localNow().toLocalDate()).size)
    }

    @Test
    fun `a MonCash payment is always paid on the hosted page and found by its transaction`() {
        val payment = created("o7", operator = "mock_moncash")
        assertNotNull(payment.checkoutUrl)
        val found = simulation.lookupPayment("mock_moncash", caller, payment.paymentId) as OperatorResponse.Answered<OperatorPayment>
        assertEquals("o7", found.body.orderId)
        assertEquals(0, sent.count { it.first == "mock_moncash" })
    }
}
