package org.openfsp.gateway.admin.session

import jakarta.servlet.http.Cookie
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.gateway.shared.web.ProblemResponses
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AdminAccessTest {
    private val hashing = AdminTokenHashing()
    private val start = Instant.parse("2026-11-09T12:00:00Z")

    private fun properties(adminToken: String?) =
        GatewayProperties(
            environment = GatewayProperties.Environment.TEST,
            developmentMode = false,
            publicBaseUrl = URI("https://gateway.example"),
            orderIdDerivationKey = "a2V5",
            adminToken = adminToken,
        )

    private fun request(
        address: String = "203.0.113.7",
        cookie: String? = null,
    ) = MockHttpServletRequest("GET", "/admin/payments").apply {
        remoteAddr = address
        cookie?.let { setCookies(Cookie(AdminSessions.COOKIE, it)) }
    }

    private fun status(
        filter: AdminAccessFilter,
        request: MockHttpServletRequest,
    ): Int {
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        filter.doFilter(request, response, chain)
        return if (chain.request != null) PASSED else response.status
    }

    @Test
    fun `the hash verifies the token it was made from and nothing else`() {
        val hash = hashing.hash("correct horse battery staple")
        assertTrue(hash.startsWith(AdminTokenHashing.PREFIX))
        assertNotEquals(hash, hashing.hash("correct horse battery staple"))
        assertTrue(hashing.matches("correct horse battery staple", hash))
        assertFalse(hashing.matches("correct horse battery stapler", hash))
        assertFalse(hashing.matches("anything", "not a hash"))
    }

    @Test
    fun `a session opens behind the hash and expires after its lifetime`() {
        val properties = properties(hashing.hash("correct horse battery staple"))
        val sessions = AdminSessions(properties, Clock.fixed(start, ZoneOffset.UTC))
        val cookie = sessions.open()
        assertTrue(cookie.isHttpOnly && cookie.isSecure && cookie.sameSite == "Strict")
        assertTrue(sessions.isValid(cookie.value))
        assertFalse(sessions.isValid(cookie.value.substringBefore('.') + ".forged"))
        assertFalse(sessions.isValid(null))
        val later = AdminSessions(properties, Clock.fixed(start.plus(Duration.ofMinutes(21)), ZoneOffset.UTC))
        assertFalse(later.isValid(cookie.value))
    }

    @Test
    fun `the admin requires a session once a token is configured`() {
        val properties = properties(hashing.hash("correct horse battery staple"))
        val sessions = AdminSessions(properties, Clock.fixed(start, ZoneOffset.UTC))
        val filter = AdminAccessFilter(properties, sessions, ProblemResponses(JsonMapper.builder().build()))
        assertEquals(UNAUTHENTICATED, status(filter, request()))
        assertEquals(UNAUTHENTICATED, status(filter, request(address = "127.0.0.1")))
        assertEquals(PASSED, status(filter, request(cookie = sessions.open().value)))
    }

    @Test
    fun `without a token the admin answers the loopback only`() {
        val properties = properties(null)
        val filter = AdminAccessFilter(properties, AdminSessions(properties, Clock.systemUTC()), ProblemResponses(JsonMapper.builder().build()))
        assertEquals(PASSED, status(filter, request(address = "127.0.0.1")))
        assertEquals(NOT_FOUND, status(filter, request()))
    }

    private companion object {
        const val PASSED = 0
        const val UNAUTHENTICATED = 401
        const val NOT_FOUND = 404
    }
}
