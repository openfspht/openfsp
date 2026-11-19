package org.openfsp.simulator

import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.common.SimulationSettings
import org.openfsp.simulator.memory.MemoryScenarioBook
import org.openfsp.simulator.memory.MemorySequences
import org.openfsp.simulator.memory.MemorySimulatedPayments
import org.openfsp.simulator.operator.Accounts
import org.openfsp.simulator.operator.Caller
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.FeeBearer
import org.openfsp.simulator.operator.OperatorFee
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.operator.spi.OperatorProfiles
import org.openfsp.simulator.payment.CreatePaymentAtOperator
import org.openfsp.simulator.payment.OperatorPayment
import org.openfsp.simulator.payment.OperatorSimulation
import org.openfsp.simulator.payment.Outcome
import org.openfsp.simulator.payment.StatementPage
import org.openfsp.simulator.scenario.Scenario
import org.openfsp.simulator.settlement.OperatorCallback
import org.openfsp.simulator.settlement.OperatorSettlement
import org.openfsp.simulator.settlement.spi.CallbackSender
import java.time.Clock
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OperatorSimulationTest {
    private val sent = mutableListOf<Pair<String, OperatorCallback>>()
    private val sender = CallbackSender { operator, _, callback -> sent.add(operator to callback) }
    private val openFsp =
        OperatorProfile("mock_openfsp", Dialect.OPENFSP, "MO", lookup = true, callbacks = true, idempotentByReference = true, statements = true, returnUrl = "https://gw.example/return/mock_openfsp")
    private val monCash = OperatorProfile("mock_moncash", Dialect.MONCASH, "MC", lookup = true, callbacks = false, idempotentByReference = false, statements = false, returnUrl = null)
    private val profiles = OperatorProfiles { listOf(openFsp, monCash).firstOrNull { profile -> profile.id == it } }
    private val payments = MemorySimulatedPayments()
    private val scenarios = MemoryScenarioBook()
    private val clock = SimulationClock(Clock.systemUTC())
    private val settings = SimulationSettings(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO)
    private val settlement = OperatorSettlement(payments, profiles, sender, { _, task -> task() }, clock, settings, {})
    private val simulation = OperatorSimulation(profiles, payments, scenarios, settlement, MemorySequences(), { "pay_${UUID.randomUUID()}" }, { "https://sim.example" }, {}, clock, settings, {})
    private val caller = Caller.ofPublishedAccount(authenticated = true, credential = "Bearer key")

    private fun create(
        reference: String,
        operator: String = "mock_openfsp",
        msisdn: String? = "50934567890",
        who: Caller = caller,
    ) = simulation.create(operator, who, CreatePaymentAtOperator(reference, "1250.00", msisdn, null, "https://gw.example/callbacks"))

    private fun created(
        reference: String,
        operator: String = "mock_openfsp",
        msisdn: String? = "50934567890",
    ) = (create(reference, operator, msisdn) as OperatorResponse.Answered<OperatorPayment>).body

    private fun lookup(reference: String) = (simulation.lookup("mock_openfsp", caller, reference) as OperatorResponse.Answered<OperatorPayment>).body

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
        assertEquals(401, (create("o2", who = Caller.ofPublishedAccount(authenticated = false, credential = "Bearer wrong")) as OperatorResponse.Refused).status)
    }

    @Test
    fun `a duplicate callback scenario calls back three times`() {
        scenarios.declare("mock_openfsp", Accounts.PUBLISHED, "o3", Scenario.DUPLICATE_CALLBACK)
        create("o3")
        assertEquals(3, sent.size)
    }

    @Test
    fun `a timeout scenario accepts the payment and answers 504 at once, without waiting for the hang duration`() {
        val paused = mutableListOf<Duration>()
        val hanging = SimulationSettings(Duration.ZERO, Duration.ZERO, Duration.ofSeconds(40), Duration.ZERO)
        val timing = OperatorSimulation(profiles, payments, scenarios, settlement, MemorySequences(), { "pay_${UUID.randomUUID()}" }, { "https://sim.example" }, { paused.add(it) }, clock, hanging, {})
        scenarios.declare("mock_openfsp", Accounts.PUBLISHED, "timeout-1", Scenario.TIMEOUT)

        val answer = timing.create("mock_openfsp", caller, CreatePaymentAtOperator("timeout-1", "1250.00", "50934567890", null, "https://gw.example/callbacks"))

        assertEquals(504, (answer as OperatorResponse.Refused).status)
        assertTrue(paused.none { it == hanging.hangDuration })
        assertEquals("SUCCESSFUL", lookup("timeout-1").status)
    }

    @Test
    fun `a statement for another provider is empty, while an operator without statements refuses it`() {
        created("statement-1")

        val other = simulation.statement("mock_openfsp", caller, "mock_other", clock.today(), clock.today(), 0, 50) as OperatorResponse.Answered<StatementPage>
        val refused = simulation.statement("mock_moncash", caller, "mock_other", clock.today(), clock.today(), 0, 50) as OperatorResponse.Refused

        assertTrue(other.body.payments.isEmpty())
        assertEquals(422, refused.status)
    }

    @Test
    fun `a contradicting operator reports success then failure`() {
        scenarios.declare("mock_openfsp", Accounts.PUBLISHED, "o4", Scenario.CONTRADICT)
        create("o4")
        assertEquals("SUCCESSFUL", lookup("o4").status)
        assertEquals("FAILED", lookup("o4").status)
    }

    @Test
    fun `a payer approval needs no checkout, a payment without payer is paid on the hosted page`() {
        assertNull(created("o5").checkoutUrl)
        scenarios.declare("mock_openfsp", Accounts.PUBLISHED, "o6", Scenario.PENDING_FOREVER)
        val hosted = created("o6", msisdn = null)
        assertEquals("https://sim.example/mock_openfsp/checkout/${hosted.paymentId}", hosted.checkoutUrl)
        assertEquals(Outcome.SUCCESSFUL, simulation.pay("mock_openfsp", hosted.paymentId, approved = true)?.outcome)
        val statement = simulation.statement("mock_openfsp", caller, "mock_openfsp", clock.today(), clock.today(), 0, 50) as OperatorResponse.Answered<StatementPage>
        assertEquals(setOf("o5", "o6"), statement.body.payments.map { it.reference }.toSet())
    }

    @Test
    fun `a MonCash payment is always paid on the hosted page and found by its transaction`() {
        val payment = created("o7", operator = "mock_moncash")
        assertNotNull(payment.checkoutUrl)
        val found = simulation.lookupPayment("mock_moncash", caller, payment.paymentId) as OperatorResponse.Answered<OperatorPayment>
        assertEquals("o7", found.body.reference)
        assertEquals(0, sent.count { it.first == "mock_moncash" })
    }

    @Test
    fun `a test profile offers only the scenarios its capability flags can produce`() {
        val statementOnly = OperatorProfile("mock_statement_only", Dialect.OPENFSP, "MT", lookup = false, callbacks = false, idempotentByReference = false, statements = true, returnUrl = null)
        assertEquals(Scenario.entries.toSet() - setOf(Scenario.DUPLICATE_CALLBACK, Scenario.LATE_CALLBACK, Scenario.UNKNOWN_STATUS, Scenario.CONTRADICT), statementOnly.scenarios)
        assertEquals(Scenario.entries.toSet(), openFsp.scenarios)
        assertFalse(Scenario.LATE_CALLBACK in statementOnly.rotation)
    }

    @Test
    fun `a configured fee is a rate of the amount rounded half up to the centime, on the openfsp dialect only`() {
        val twoPercent = OperatorFee(200, FeeBearer.MERCHANT)
        assertEquals(2_500, twoPercent.on(125_000))
        assertEquals(1, twoPercent.on(25))
        assertEquals(0, twoPercent.on(24))
        assertEquals(0, OperatorFee(0, FeeBearer.PAYER).on(125_000))
        assertFailsWith<IllegalArgumentException> { OperatorFee(10_001, FeeBearer.PAYER) }
        assertFailsWith<IllegalArgumentException> { monCash.copy(fee = twoPercent) }
        assertEquals(twoPercent, openFsp.copy(fee = twoPercent).fee)
    }
}
