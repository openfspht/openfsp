package org.openfsp.gateway.admin.session

import jakarta.servlet.http.Cookie
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.gateway.shared.security.AdminTokenHashing
import org.openfsp.gateway.shared.security.ApplicationKey
import org.openfsp.gateway.shared.web.ProblemResponses
import org.openfsp.gateway.shared.web.RequestPaths
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

class InMemoryAdminSessionStore : AdminSessionStore {
    private val sessions = mutableMapOf<String, Instant>()

    override fun open(
        id: String,
        expiresAt: Instant,
        now: Instant,
    ) {
        sessions[id] = expiresAt
    }

    override fun isOpen(
        id: String,
        now: Instant,
    ): Boolean = sessions[id]?.isAfter(now) == true

    override fun close(id: String) {
        sessions.remove(id)
    }
}

class AdminAccessTest {
    private val store = InMemoryAdminSessionStore()
    private val hashing = AdminTokenHashing()
    private val start = Instant.parse("2026-11-09T12:00:00Z")
    private val applicationKey = ApplicationKey(java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 3 }))

    private fun properties(
        adminToken: String?,
        developmentMode: Boolean = false,
    ) = GatewayProperties(
            environment = GatewayProperties.Environment.TEST,
            developmentMode = developmentMode,
            publicBaseUrl = URI("https://gateway.example"),
            appKey = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 3 }),
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
        val sessions = AdminSessions(properties, Clock.fixed(start, ZoneOffset.UTC), applicationKey, store)
        val cookie = sessions.open()
        assertTrue(cookie.isHttpOnly && cookie.isSecure && cookie.sameSite == "Strict")
        assertTrue(sessions.isValid(cookie.value))
        assertFalse(sessions.isValid(cookie.value.substringBefore('.') + ".forged"))
        assertFalse(sessions.isValid(null))
        val later = AdminSessions(properties, Clock.fixed(start.plus(Duration.ofMinutes(21)), ZoneOffset.UTC), applicationKey, store)
        assertFalse(later.isValid(cookie.value))
    }

    @Test
    fun `a session survives a restart and serves every replica until the admin token changes`() {
        val properties = properties(hashing.hash("correct horse battery staple"))
        val clock = Clock.fixed(start, ZoneOffset.UTC)
        val cookie = AdminSessions(properties, clock, applicationKey, store).open()
        assertTrue(AdminSessions(properties, clock, applicationKey, store).isValid(cookie.value))
        assertFalse(AdminSessions(properties(hashing.hash("another token entirely")), clock, applicationKey, store).isValid(cookie.value))
    }

    @Test
    fun `signing out revokes the session on the server, even for a copy of its cookie`() {
        val properties = properties(hashing.hash("correct horse battery staple"))
        val sessions = AdminSessions(properties, Clock.fixed(start, ZoneOffset.UTC), applicationKey, store)
        val cookie = sessions.open()
        val copy = cookie.value
        assertTrue(sessions.isValid(copy))
        sessions.close(cookie.value)
        assertFalse(sessions.isValid(copy))
    }

    @Test
    fun `the admin requires a session once a token is configured`() {
        val properties = properties(hashing.hash("correct horse battery staple"))
        val sessions = AdminSessions(properties, Clock.fixed(start, ZoneOffset.UTC), applicationKey, store)
        val filter = AdminAccessFilter(properties, sessions, ProblemResponses(JsonMapper.builder().build(), RequestPaths()), RequestPaths())
        assertEquals(UNAUTHENTICATED, status(filter, request()))
        assertEquals(UNAUTHENTICATED, status(filter, request(address = "127.0.0.1")))
        assertEquals(PASSED, status(filter, request(cookie = sessions.open().value)))
    }

    @Test
    fun `without a token the admin answers the loopback only, in development mode only`() {
        val development = properties(null, developmentMode = true)
        val open = AdminAccessFilter(development, AdminSessions(development, Clock.systemUTC(), applicationKey, store), ProblemResponses(JsonMapper.builder().build(), RequestPaths()), RequestPaths())
        assertEquals(PASSED, status(open, request(address = "127.0.0.1")))
        assertEquals(NOT_FOUND, status(open, request()))

        val served = properties(null)
        val closed = AdminAccessFilter(served, AdminSessions(served, Clock.systemUTC(), applicationKey, store), ProblemResponses(JsonMapper.builder().build(), RequestPaths()), RequestPaths())
        assertEquals(NOT_FOUND, status(closed, request(address = "127.0.0.1")))
    }

    private companion object {
        const val PASSED = 0
        const val UNAUTHENTICATED = 401
        const val NOT_FOUND = 404
    }
}
