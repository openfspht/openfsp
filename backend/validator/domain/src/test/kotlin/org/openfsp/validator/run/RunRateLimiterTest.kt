package org.openfsp.validator.run

import org.openfsp.validator.problem.ProblemKind
import org.openfsp.validator.problem.ValidatorException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RunRateLimiterTest {
    private var now = Instant.parse("2026-11-11T10:00:00Z")
    private val clock =
        object : Clock() {
            override fun getZone() = ZoneOffset.UTC

            override fun withZone(zone: java.time.ZoneId?) = this

            override fun instant() = now
        }
    private val limiter = RunRateLimiter(TestSettings.run(runsPerAddress = 2, runsWindow = Duration.ofHours(1)), clock)

    @Test
    fun `an address gets its runs per window, then waits for the oldest to leave the window`() {
        limiter.acquire("203.0.113.7")
        now = now.plusSeconds(600)
        limiter.acquire("203.0.113.7")
        val refused = assertFailsWith<ValidatorException> { limiter.acquire("203.0.113.7") }
        assertEquals(ProblemKind.RATE_LIMITED, refused.kind)
        assertEquals(Duration.ofMinutes(50), refused.retryAfter)
        limiter.acquire("198.51.100.1")
        now = now.plus(Duration.ofMinutes(51))
        limiter.acquire("203.0.113.7")
    }
}
