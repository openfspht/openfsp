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
import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.payment.spi.SimulatedPayments
import org.openfsp.simulator.payment.spi.stubs.InMemorySimulatedPayments
import org.openfsp.simulator.scenario.spi.stubs.InMemoryScenarioBook
import org.openfsp.simulator.settlement.OperatorSettlement
import java.time.Clock
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class IdempotentCreationTest {
    private val openFsp = OperatorProfile("mock_openfsp", Dialect.OPENFSP, "MO", lookup = true, callbacks = true, idempotentByReference = true, statements = true, returnUrl = null)
    private val profiles = OperatorProfiles { id -> openFsp.takeIf { it.id == id } }
    private val stored = InMemorySimulatedPayments()
    private var beforeNextCheck: (() -> Unit)? = null
    private val payments =
        object : SimulatedPayments by stored {
            override fun withReference(
                operator: String,
                reference: String,
            ): SimulatedPayment? =
                stored.withReference(operator, reference).also {
                    val interleaved = beforeNextCheck
                    beforeNextCheck = null
                    interleaved?.invoke()
                }
        }
    private val clock = SimulationClock(Clock.systemUTC())
    private val settings = SimulationSettings(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO)
    private val settlement = OperatorSettlement(payments, profiles, { _, _, _ -> true }, { _, _ -> }, clock, settings)
    private val simulation =
        OperatorSimulation(profiles, payments, InMemoryScenarioBook(), settlement, InMemorySequences(), { "pay_${UUID.randomUUID()}" }, { "https://sim.example" }, {}, clock, settings)
    private val caller = Caller(authenticated = true, credential = "Bearer key")

    private fun create(reference: String) = simulation.create("mock_openfsp", caller, CreatePaymentAtOperator(reference, "100.00", "+50934567890", null, null))

    @Test
    fun `two concurrent creations with one reference keep a single payment`() {
        beforeNextCheck = { create("ref-race") }

        val second = create("ref-race") as OperatorResponse.Answered<OperatorPayment>

        assertEquals(1, stored.all().size)
        assertEquals(stored.all().single().paymentId, second.body.paymentId)
        assertEquals(200, second.status)
    }
}
