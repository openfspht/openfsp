package org.openfsp.server.webhook

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeliveryScheduleTest {
    private val schedule = DeliverySchedule()

    @Test
    fun `each delay follows the minimal schedule with a positive jitter of 20 to 40 percent`() {
        DeliverySchedule.DELAYS.forEachIndexed { index, base ->
            assertEquals(base.plusMillis(base.toMillis() / 5), schedule.delayAfter(index + 1, null, null, 0.0))
            assertEquals(base.plusMillis(base.toMillis() * 2 / 5), schedule.delayAfter(index + 1, 500, null, 1.0))
            val random = schedule.delayAfter(index + 1, null, null, Math.random())!!
            assertTrue(random >= base.plusMillis(base.toMillis() / 5) && random <= base.plusMillis(base.toMillis() * 2 / 5), "$random for $base")
        }
    }

    @Test
    fun `the seventh attempt is the last`() {
        assertEquals(31 * 3600 + 12 * 60 + 30L, DeliverySchedule.DELAYS.sumOf { it.seconds })
        assertNull(schedule.delayAfter(DeliverySchedule.DELAYS.size + 1, 500, null, 0.0))
    }

    @Test
    fun `Retry-After is honoured for 429 and 503 only, up to one hour, never below the schedule`() {
        assertEquals(Duration.ofMinutes(5), schedule.delayAfter(1, 429, Duration.ofMinutes(5), 0.0))
        assertEquals(Duration.ofHours(1), schedule.delayAfter(1, 503, Duration.ofDays(1), 0.0))
        assertEquals(Duration.ofSeconds(36), schedule.delayAfter(1, 429, Duration.ofSeconds(1), 0.0))
        assertEquals(Duration.ofSeconds(36), schedule.delayAfter(1, 500, Duration.ofMinutes(5), 0.0))
        assertEquals(Duration.ofSeconds(42), schedule.delayAfter(1, null, null, 1.0))
    }
}
