package org.openfsp.validator.engine

import org.openfsp.validator.engine.spi.Transport
import org.openfsp.validator.engine.spi.stubs.SteppedTime
import org.openfsp.validator.json.spi.stubs.PlainJsonCodec
import org.openfsp.validator.suite.ControlAction
import org.openfsp.validator.suite.Settlement
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OutcomeControlTest {
    private val time = SteppedTime()

    private fun control(settledFrom: Int): Pair<OutcomeControl, AtomicInteger> {
        val reads = AtomicInteger()
        val transport =
            Transport { request ->
                val settled = reads.incrementAndGet() >= settledFrom
                Exchange(request, 200, mapOf(Expectations.CONTENT_TYPE to listOf(Expectations.JSON)), """{"payment":"pay_1","settled":$settled,"callbacks_sent":0}""".toByteArray())
            }
        return OutcomeControl("http://target.test/control", "ofsp_test_key", transport, PlainJsonCodec, time, time) to reads
    }

    private fun variables() = Variables("run", 1, emptyMap(), emptyMap(), PlainJsonCodec)

    @Test
    fun `waiting for a settlement polls at the given pace on the injected clock, without sleeping`() {
        val (control, reads) = control(settledFrom = 3)

        control.perform(ControlAction(settled = Settlement("order-1", within = "10s", every = "2s")), variables())

        assertEquals(3, reads.get())
        assertEquals(listOf(Duration.ofSeconds(2), Duration.ofSeconds(2)), time.pauses)
    }

    @Test
    fun `a settlement that never comes fails once the injected clock passes the deadline`() {
        val (control, _) = control(settledFrom = Int.MAX_VALUE)

        val failure = assertFailsWith<PreparationException> { control.perform(ControlAction(settled = Settlement("order-1", within = "5s", every = "1s")), variables()) }

        assertTrue(failure.message!!.contains("within 5s"))
        assertEquals(Duration.ofSeconds(5), time.pauses.fold(Duration.ZERO, Duration::plus))
    }
}
