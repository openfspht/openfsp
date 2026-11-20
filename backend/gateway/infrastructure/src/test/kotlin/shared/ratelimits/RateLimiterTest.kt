package org.openfsp.gateway.shared.ratelimits

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RateLimiterTest {
    private class MovingClock : Clock() {
        var now: Instant = Instant.parse("2026-11-15T12:00:00Z")

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }

    private val clock = MovingClock()

    @Test
    fun `a key that spent its burst waits for the next token`() {
        val limiter = RateLimiter(ratePerSecond = 0.5, burst = 2.0, clock = clock)

        assertNull(limiter.acquire("a"))
        assertNull(limiter.acquire("a"))
        assertEquals(Duration.ofSeconds(2), limiter.acquire("a"))
    }

    @Test
    fun `the table never holds more keys than its capacity`() {
        val limiter = RateLimiter(ratePerSecond = 1.0, burst = 1.0, clock = clock, capacity = 3)

        repeat(1_000) {
            limiter.acquire("key-$it")
            clock.now = clock.now.plusMillis(10)
            assertTrue(limiter.size <= 3)
        }
    }

    @Test
    fun `a full table never refuses a new key, it evicts the least recently used one`() {
        val limiter = RateLimiter(ratePerSecond = 0.001, burst = 1.0, clock = clock, capacity = 2)
        repeat(10) { assertNull(limiter.acquire("exhausted-$it")) }

        assertNull(limiter.acquire("newcomer"))
        assertEquals(2, limiter.size)
    }

    @Test
    fun `a key that keeps trying stays limited while others come and go`() {
        val limiter = RateLimiter(ratePerSecond = 0.1, burst = 1.0, clock = clock, capacity = 2)
        assertNull(limiter.acquire("limited"))

        repeat(10) {
            assertNull(limiter.acquire("intruder-$it"))
            assertEquals(Duration.ofSeconds(10), limiter.acquire("limited"))
        }
    }
}
