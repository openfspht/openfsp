package org.openfsp.validator.web.runs

import org.openfsp.validator.web.configuration.ValidatorProperties
import org.openfsp.validator.web.problems.ProblemKind
import org.openfsp.validator.web.problems.ValidatorException
import java.net.InetAddress
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
    private val limiter = RunRateLimiter(ValidatorProperties(runsPerAddress = 2, runsWindow = Duration.ofHours(1)), clock)

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

class TargetGuardTest {
    private val addresses = mutableMapOf<String, List<InetAddress>>()
    private val guard = TargetGuard(ValidatorProperties(developmentMode = false)) { host -> addresses[host].orEmpty() }

    @Test
    fun `a host resolving to one private address among public ones is refused`() {
        addresses["mixed.example"] = listOf(InetAddress.getByName("93.184.215.14"), InetAddress.getByName("10.0.0.5"))
        assertEquals(ProblemKind.PRIVATE_ADDRESS, assertFailsWith<ValidatorException> { guard.pin(java.net.URI("https://mixed.example")) }.kind)
    }

    @Test
    fun `a public host is accepted and an unknown one refused`() {
        addresses["gateway.example"] = listOf(InetAddress.getByName("93.184.215.14"))
        assertEquals(addresses["gateway.example"], guard.pin(java.net.URI("https://gateway.example/v1")).addresses)
        assertEquals(ProblemKind.UNRESOLVABLE_HOST, assertFailsWith<ValidatorException> { guard.pin(java.net.URI("https://nowhere.example")) }.kind)
    }

    @Test
    fun `development mode accepts the loopback gateway`() {
        val pinned = TargetGuard(ValidatorProperties(developmentMode = true)) { listOf(InetAddress.getByName(it)) }.pin(java.net.URI("http://127.0.0.1:8080"))
        assertEquals(listOf(InetAddress.getByName("127.0.0.1")), pinned.addresses)
    }
}
