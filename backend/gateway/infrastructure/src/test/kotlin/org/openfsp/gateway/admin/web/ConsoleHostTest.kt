package org.openfsp.gateway.admin.web

import org.openfsp.gateway.TestcontainersConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["openfsp.admin-host=console.gateway.test"])
@AutoConfigureMockMvc
@ActiveProfiles("development", "test")
class ConsoleHostTest {
    @Autowired
    private lateinit var mvc: MockMvc

    private fun get(
        path: String,
        host: String,
        vararg headers: Pair<String, String>,
    ): MvcResult =
        mvc
            .get(path) {
                header(HttpHeaders.HOST, host)
                headers.forEach { (name, value) -> header(name, value) }
            }.andReturn()

    @Test
    fun `the admin host serves the console pages, its assets and the admin API`() {
        listOf("/", "/sign-in", "/activity", "/payments/pay_123", "/accounts/acct_1").forEach { path ->
            val page = get(path, ADMIN)
            assertEquals(200, page.response.status, path)
            assertTrue(page.response.contentAsString.contains("<div id=\"root\">"), path)
            assertEquals("no-cache", page.response.getHeader(HttpHeaders.CACHE_CONTROL), path)
        }
        assertEquals(200, get("/assets/console-test.js", ADMIN).response.status)
        assertEquals(200, get("/admin/overview", ADMIN).response.status)
        assertEquals(200, get("/admin/overview", "Console.Gateway.Test:8443").response.status)
    }

    @Test
    fun `any other host answers the problem 404 for the console and the admin API`() {
        listOf("/", "/sign-in", "/activity", "/assets/console-test.js", "/monitoring.json", "/admin/overview", "/admin/session", "/admin").forEach { path ->
            val result = get(path, PUBLIC)
            assertEquals(404, result.response.status, path)
            assertTrue(result.response.contentType!!.startsWith("application/problem+json"), path)
        }
        assertEquals(404, mvc.post("/admin/session") { header(HttpHeaders.HOST, PUBLIC) }.andReturn().response.status)
    }

    @Test
    fun `a forwarded host from a peer that is not a trusted proxy is ignored`() {
        assertEquals(404, get("/admin/overview", PUBLIC, "X-Forwarded-Host" to ADMIN).response.status)
        assertEquals(404, get("/activity", PUBLIC, "Forwarded" to "host=$ADMIN").response.status)
    }

    @Test
    fun `encoded and ambiguous admin paths never reach the API from another host`() {
        assertEquals(404, get("/%61dmin/overview", PUBLIC).response.status)
        listOf("/admin/..;/admin/overview", "/assets/../admin/overview", "/admin;x/overview").forEach { path ->
            assertEquals(400, get(path, ADMIN).response.status, path)
        }
    }

    @Test
    fun `the public API keeps answering on both hosts and unknown paths stay problem 404s`() {
        listOf(ADMIN, PUBLIC).forEach { host ->
            assertEquals(401, get("/v1/payments", host).response.status, host)
            assertEquals(200, get("/.well-known/openfsp-configuration", host).response.status, host)
            val unknown = get("/v1/unknown", host, HttpHeaders.AUTHORIZATION to "Bearer nope")
            assertEquals(401, unknown.response.status, host)
            val outside = get("/nothing-here", host)
            assertEquals(404, outside.response.status, host)
            assertTrue(outside.response.contentType!!.startsWith("application/problem+json"), host)
        }
    }

    @Test
    fun `console responses carry the security headers`() {
        val page = get("/activity", ADMIN).response
        assertTrue(page.getHeader("Content-Security-Policy")!!.contains("frame-ancestors 'none'"))
        assertEquals("nosniff", page.getHeader("X-Content-Type-Options"))
        assertEquals("DENY", page.getHeader("X-Frame-Options"))
        assertEquals("no-referrer", page.getHeader("Referrer-Policy"))
        assertTrue(page.getHeader("Permissions-Policy")!!.contains("camera=()"))
    }

    private companion object {
        const val ADMIN = "console.gateway.test"
        const val PUBLIC = "gateway.test"
    }
}
