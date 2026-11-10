package org.openfsp.simulator

import org.openfsp.simulator.operator.CreatePaymentAtOperator
import org.openfsp.simulator.operator.OperatorCallback
import org.openfsp.simulator.operator.OperatorPayment
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.operator.OperatorSettlement
import org.openfsp.simulator.operator.OperatorSimulation
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

class OperatorSimulationTest {
    private val sent = mutableListOf<Pair<OperatorCallback, String?>>()
    private val sender = CallbackSender { _, callback, secret -> sent.add(callback to secret) }
    private val alpha = OperatorProfile("mock_alpha", "MA", "alpha_key", "alpha_secret", lookup = true, callbacks = true, idempotentByOrder = true, statements = false, returnUrl = null)
    private val epsilon = OperatorProfile("mock_epsilon", "ME", "epsilon_key", null, lookup = false, callbacks = true, idempotentByOrder = false, statements = true, returnUrl = "https://gw.example/return/mock_epsilon")
    private val profiles = OperatorProfiles { listOf(alpha, epsilon).firstOrNull { profile -> profile.id == it } }
    private val payments = InMemorySimulatedPayments()
    private val scenarios = InMemoryScenarioBook()
    private val statements = InMemoryStatementBook()
    private val clock = SimulationClock(Clock.systemUTC())
    private val settings = SimulationSettings(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, "https://sim.example")
    private val settlement = OperatorSettlement(payments, profiles, statements, sender, { _, task -> task() }, clock, settings)
    private val simulation = OperatorSimulation(profiles, payments, scenarios, statements, settlement, {}, clock, settings)

    private fun create(
        orderId: String,
        operator: String = "mock_alpha",
        key: String = "alpha_key",
    ) = simulation.create(operator, "Bearer $key", CreatePaymentAtOperator(orderId, "1250.00", "50934567890", null, "https://gw.example/callbacks"))

    private fun lookup(orderId: String) = (simulation.lookup("mock_alpha", "Bearer alpha_key", orderId) as OperatorResponse.Answered<OperatorPayment>).body

    @Test
    fun `a payment without a declared scenario succeeds and calls back once, signed`() {
        assertIs<OperatorResponse.Answered<OperatorPayment>>(create("o1"))
        assertEquals("SUCCESSFUL", lookup("o1").status)
        assertEquals(listOf("alpha_secret"), sent.map { it.second })
    }

    @Test
    fun `a wrong key is refused`() {
        assertEquals(401, (create("o2", key = "wrong") as OperatorResponse.Refused).status)
    }

    @Test
    fun `a duplicate callback scenario calls back three times`() {
        scenarios.declare("mock_alpha", "o3", Scenario.DUPLICATE_CALLBACK)
        create("o3")
        assertEquals(3, sent.size)
    }

    @Test
    fun `a contradicting operator reports success then failure`() {
        scenarios.declare("mock_alpha", "o4", Scenario.CONTRADICT)
        create("o4")
        assertEquals("SUCCESSFUL", lookup("o4").status)
        assertEquals("FAILED", lookup("o4").status)
    }

    @Test
    fun `a hosted checkout redirects to the return point and books the payment`() {
        val created = (create("o5", "mock_epsilon", "epsilon_key") as OperatorResponse.Answered<OperatorPayment>).body
        assertEquals("REDIRECT", created.approval)
        scenarios.declare("mock_epsilon", "o6", Scenario.PENDING_FOREVER)
        val pending = (create("o6", "mock_epsilon", "epsilon_key") as OperatorResponse.Answered<OperatorPayment>).body
        assertEquals("https://gw.example/return/mock_epsilon?order_id=o6", simulation.pay("mock_epsilon", pending.paymentId, approved = true))
        assertEquals(2, statements.lines("mock_epsilon", clock.localNow().toLocalDate()).size)
    }
}
