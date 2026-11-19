package org.openfsp.simulator

import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.idempotency.CreationAttempt
import org.openfsp.simulator.idempotency.IdempotencyScope
import org.openfsp.simulator.idempotency.IdempotentCreation
import org.openfsp.simulator.idempotency.IdempotentCreations
import org.openfsp.simulator.idempotency.RecordedResponse
import org.openfsp.simulator.idempotency.spi.stubs.InMemoryIdempotencyRecords
import org.openfsp.simulator.operator.Accounts
import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.payment.spi.stubs.InMemorySimulatedPayments
import org.openfsp.simulator.scenario.Scenario
import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IdempotentCreationsTest {
    private val records = InMemoryIdempotencyRecords()
    private val payments = InMemorySimulatedPayments()
    private val creations = IdempotentCreations(records, payments, SimulationClock(Clock.systemUTC()))
    private val scope = IdempotencyScope("mock_openfsp", Accounts.PUBLISHED, "PRIMARY", "key-1")
    private val created = RecordedResponse(201, "{}", "pay_1")

    private fun hold(paymentId: String) =
        payments.save(SimulatedPayment("mock_openfsp", paymentId, "ref-1", "100.00", null, null, Scenario.SUCCESS, Instant.now(), Instant.now().plusSeconds(900), account = Accounts.PUBLISHED))

    @Test
    fun `a determined answer is replayed for the same body and refused for another one`() {
        hold("pay_1")
        assertEquals(IdempotentCreation.Answered(created), creations.create(scope, "f1") { CreationAttempt.Determined(created) })

        assertEquals(IdempotentCreation.Replayed(created), creations.create(scope, "f1") { error("not attempted again") })
        assertEquals(IdempotentCreation.Reused, creations.create(scope, "f2") { error("not attempted again") })
    }

    @Test
    fun `an undetermined answer or a failure leaves the key free for a new attempt`() {
        val failed = RecordedResponse(502, "{}", null)
        assertEquals(IdempotentCreation.Answered(failed), creations.create(scope, "f1") { CreationAttempt.Undetermined(failed) })
        assertTrue(records.records.isEmpty())

        assertFailsWith<IllegalStateException> { creations.create(scope, "f1") { error("lost") } }
        assertTrue(records.records.isEmpty())
    }

    @Test
    fun `a creation still running answers in progress, and a record whose payment is gone is attempted again`() {
        var concurrent: IdempotentCreation? = null
        creations.create(scope, "f1") {
            concurrent = creations.create(scope, "f1") { error("not attempted while the first runs") }
            CreationAttempt.Undetermined(created)
        }
        assertIs<IdempotentCreation.InProgress>(concurrent)

        creations.create(scope, "f1") { CreationAttempt.Determined(created) }
        val again = creations.create(scope, "f1") { CreationAttempt.Determined(created.copy(paymentId = "pay_2")) }
        assertEquals(IdempotentCreation.Answered(created.copy(paymentId = "pay_2")), again)
    }
}
