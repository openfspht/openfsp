package org.openfsp.simulator

import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.emission.ConformanceRun
import org.openfsp.simulator.emission.DeliveryResult
import org.openfsp.simulator.emission.EncodedEvent
import org.openfsp.simulator.emission.EventEmission
import org.openfsp.simulator.emission.RetrySchedule
import org.openfsp.simulator.emission.spi.ConformanceRuns
import org.openfsp.simulator.emission.spi.EventTransport
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.spi.OperatorProfiles
import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.scenario.Scenario
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventEmissionTest {
    private val emitting = OperatorProfile("mock_openfsp", Dialect.OPENFSP, "MO", lookup = true, callbacks = true, idempotentByReference = true, statements = true, returnUrl = null, emitsEvents = true)
    private val clock = SimulationClock(Clock.systemUTC())
    private val held = mutableMapOf<String, ConformanceRun>()
    private val runs =
        object : ConformanceRuns {
            override fun save(run: ConformanceRun) = run.also { held[it.id] = it }

            override fun find(id: String) = held[id]

            override fun all() = held.values.toList()

            override fun clear() = held.clear()
        }
    private val answers = ArrayDeque<Int>()
    private val attempts = mutableListOf<Int>()
    private val abandoned = mutableListOf<Int>()
    private val bodies = mutableListOf<ByteArray>()
    private val transport =
        object : EventTransport {
            override fun deliver(
                operator: String,
                url: String,
                event: EncodedEvent,
                attempt: Int,
            ): DeliveryResult {
                attempts += attempt
                bodies += event.body
                return DeliveryResult(answers.removeFirstOrNull() ?: NO_CONTENT)
            }

            override fun abandoned(
                url: String,
                event: EncodedEvent,
                attempt: Int,
            ) {
                abandoned += attempt
            }
        }
    private val delays = mutableListOf<Duration>()
    private val emission =
        EventEmission(
            { id -> emitting.takeIf { it.id == id } },
            runs,
            { EncodedEvent("evt_${it.paymentId}_1", "payment.created", "{\"id\":\"${it.paymentId}\"}".toByteArray()) },
            transport,
            { delay, task -> delays += delay; task() },
            clock,
        )

    private fun payment(run: String?): SimulatedPayment {
        val now = Instant.now()
        return SimulatedPayment("mock_openfsp", "pay_1", "ref-1", "1250.00", null, null, Scenario.PENDING_FOREVER, now, now.plusSeconds(900), run = run)
    }

    private fun run(lifetime: Duration = Duration.ofHours(1)) = emission.open("mock_openfsp", "ofsp_test_run", "https://receiver.example/hooks", lifetime).id

    @Test
    fun `a delivery answered 500 is retried after at least 30 seconds with the same body as attempt 2`() {
        answers += SERVER_ERROR
        emission.emit(payment(run()))

        assertEquals(listOf(1, 2), attempts)
        assertTrue(delays[1] >= Duration.ofSeconds(36))
        assertTrue(bodies[0].contentEquals(bodies[1]))
    }

    @Test
    fun `a delivery answered 410 is never retried`() {
        answers += GONE
        emission.emit(payment(run()))

        assertEquals(listOf(1), attempts)
        assertEquals(emptyList(), abandoned)
    }

    @Test
    fun `a delivery that keeps failing is abandoned after the seventh attempt`() {
        repeat(ATTEMPTS) { answers += SERVER_ERROR }
        emission.emit(payment(run()))

        assertEquals((1..ATTEMPTS).toList(), attempts)
        assertEquals(listOf(ATTEMPTS), abandoned)
    }

    @Test
    fun `a payment outside an active run or of an operator that does not emit delivers nothing`() {
        emission.emit(payment(null))
        emission.emit(payment(run(Duration.ZERO)))
        emission.emit(payment(run()).copy(operator = "mock_other"))

        assertEquals(emptyList(), attempts)
    }

    @Test
    fun `a run key authenticates only while its run is active`() {
        run()

        assertEquals("mock_openfsp", emission.active("mock_openfsp", "ofsp_test_run")?.operator)
        assertNull(emission.active("mock_openfsp", "ofsp_test_other"))
        assertNull(emission.active("mock_other", "ofsp_test_run"))
    }

    @Test
    fun `Retry-After is honored for 429 and 503 up to an hour`() {
        assertEquals(Duration.ofMinutes(5), RetrySchedule.delayAfter(1, DeliveryResult(TOO_MANY, Duration.ofMinutes(5)), 0.0))
        assertEquals(Duration.ofHours(1), RetrySchedule.delayAfter(1, DeliveryResult(UNAVAILABLE, Duration.ofDays(1)), 0.0))
        assertEquals(Duration.ofSeconds(36), RetrySchedule.delayAfter(1, DeliveryResult(SERVER_ERROR, Duration.ofMinutes(5)), 0.0))
        assertEquals(Duration.ofSeconds(42), RetrySchedule.delayAfter(1, DeliveryResult(null), 1.0))
    }

    private companion object {
        const val NO_CONTENT = 204
        const val GONE = 410
        const val TOO_MANY = 429
        const val SERVER_ERROR = 500
        const val UNAVAILABLE = 503
        const val ATTEMPTS = 7
    }
}
