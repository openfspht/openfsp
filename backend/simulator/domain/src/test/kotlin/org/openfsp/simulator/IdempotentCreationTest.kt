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
import org.openfsp.simulator.space.SpaceId
import org.openfsp.simulator.statement.spi.stubs.InMemoryStatementBook
import java.time.Clock
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class IdempotentCreationTest {
    private val space = SpaceId.DEFAULT
    private val openFsp = OperatorProfile("mock_openfsp", Dialect.OPENFSP, "MO", lookup = true, callbacks = true, idempotentByReference = true, statements = true, returnUrl = null)
    private val profiles = OperatorProfiles { id -> openFsp.takeIf { it.id == id } }
    private val stored = InMemorySimulatedPayments()
    private var beforeNextCheck: (() -> Unit)? = null
    private val payments =
        object : SimulatedPayments by stored {
            override fun withReference(
                space: SpaceId,
                operator: String,
                reference: String,
            ): SimulatedPayment? =
                stored.withReference(space, operator, reference).also {
                    val interleaved = beforeNextCheck
                    beforeNextCheck = null
                    interleaved?.invoke()
                }
        }
    private val statements = InMemoryStatementBook()
    private val clock = SimulationClock(Clock.systemUTC())
    private val settings = SimulationSettings(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO)
    private val settlement = OperatorSettlement(payments, profiles, statements, { _, _, _ -> true }, { _, _ -> }, clock, settings)
    private val simulation =
        OperatorSimulation(profiles, payments, InMemoryScenarioBook(), statements, settlement, InMemorySequences(), { "pay_${UUID.randomUUID()}" }, { "https://sim.example" }, {}, clock, settings)
    private val caller = Caller(authenticated = true, credential = "Bearer key")

    private fun create(reference: String) = simulation.create(space, "mock_openfsp", caller, CreatePaymentAtOperator(reference, "100.00", "+50934567890", null, null))

    @Test
    fun `two concurrent creations with one reference keep a single payment`() {
        beforeNextCheck = { create("ref-race") }

        val second = create("ref-race") as OperatorResponse.Answered<OperatorPayment>

        assertEquals(1, stored.all(space).size)
        assertEquals(stored.all(space).single().paymentId, second.body.paymentId)
        assertEquals(200, second.status)
    }
}
