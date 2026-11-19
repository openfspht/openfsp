package org.openfsp.ui

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.head
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(classes = [UserInterfaceTest.Application::class], properties = ["openfsp.ui.sentry.dsn=https://key@o1.ingest.sentry.io/1", "openfsp.ui.sentry.traces-sample-rate=0.2"])
@AutoConfigureMockMvc
class UserInterfaceTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Test
    fun `every interface route answers the index page, never cached`() {
        listOf("/", "/items", "/items/42").forEach { path ->
            val result = mvc.get(path).andReturn()
            assertEquals(200, result.response.status, path)
            assertTrue(result.response.contentType!!.startsWith("text/html"), path)
            assertTrue(result.response.contentAsString.contains("<div id=\"root\">"), path)
            assertEquals("no-cache", result.response.getHeader(HttpHeaders.CACHE_CONTROL), path)
        }
    }

    @Test
    fun `a head request on a route answers like a get`() {
        assertEquals(200, mvc.head("/items").andReturn().response.status)
    }

    @Test
    fun `paths outside the interface routes are left to the application`() {
        assertEquals("""{"a":1}""", mvc.get("/api/things").andReturn().response.contentAsString)
        listOf("/api/unknown", "/items/1/2", "/index.html", "/unknown").forEach { path ->
            val result = mvc.get(path).andReturn()
            assertEquals(404, result.response.status, path)
            assertNull(result.response.getHeader(UiHeadersFilter.CONTENT_SECURITY_POLICY), path)
        }
    }

    @Test
    fun `hashed assets are cached for a year and immutable`() {
        val result = mvc.get("/assets/app-1a2b3c.js").andReturn()
        assertEquals(200, result.response.status)
        assertEquals("max-age=31536000, public, immutable", result.response.getHeader(HttpHeaders.CACHE_CONTROL))
        assertEquals("console.log(\"ui\")\n", result.response.contentAsString)
        assertEquals(404, mvc.get("/assets/missing.js").andReturn().response.status)
    }

    @Test
    fun `a client that accepts gzip receives the precompressed variant`() {
        val asset = mvc.get("/assets/app-1a2b3c.js") { header(HttpHeaders.ACCEPT_ENCODING, "gzip, br") }.andReturn()
        assertEquals("gzip", asset.response.getHeader(HttpHeaders.CONTENT_ENCODING))
        assertTrue(asset.response.getHeaders(HttpHeaders.VARY).any { it.contains(HttpHeaders.ACCEPT_ENCODING) })
        val page = mvc.get("/") { header(HttpHeaders.ACCEPT_ENCODING, "br;q=1, gzip") }.andReturn()
        assertEquals("gzip", page.response.getHeader(HttpHeaders.CONTENT_ENCODING))
        val refused = mvc.get("/") { header(HttpHeaders.ACCEPT_ENCODING, "gzip;q=0") }.andReturn()
        assertNull(refused.response.getHeader(HttpHeaders.CONTENT_ENCODING))
    }

    @Test
    fun `interface responses carry the security headers`() {
        listOf(mvc.get("/items").andReturn(), mvc.get("/assets/app-1a2b3c.js").andReturn(), mvc.get(UiPaths.SETTINGS).andReturn()).forEach(::assertSecured)
        assertNull(mvc.get("/").andReturn().response.getHeader(UiHeadersFilter.STRICT_TRANSPORT_SECURITY))
        assertEquals(UiHeadersFilter.HSTS, mvc.get("/") { secure = true }.andReturn().response.getHeader(UiHeadersFilter.STRICT_TRANSPORT_SECURITY))
    }

    @Test
    fun `the monitoring settings come from the configuration and are never stored`() {
        val result = mvc.get(UiPaths.SETTINGS).andReturn()
        assertEquals("no-store", result.response.getHeader(HttpHeaders.CACHE_CONTROL))
        assertEquals(
            """{"dsn":"https://key@o1.ingest.sentry.io/1","environment":"","release":"","tracesSampleRate":"0.2"}""",
            result.response.contentAsString,
        )
    }

    private fun assertSecured(result: MvcResult) {
        val response = result.response
        assertEquals(POLICY, response.getHeader(UiHeadersFilter.CONTENT_SECURITY_POLICY))
        assertEquals("nosniff", response.getHeader(UiHeadersFilter.CONTENT_TYPE_OPTIONS))
        assertEquals("DENY", response.getHeader(UiHeadersFilter.FRAME_OPTIONS))
        assertEquals("no-referrer", response.getHeader(UiHeadersFilter.REFERRER_POLICY))
        assertEquals(UiHeadersFilter.NO_PERMISSIONS, response.getHeader(UiHeadersFilter.PERMISSIONS_POLICY))
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    class Application {
        @Bean
        fun userInterface() = UserInterface(listOf("/", "/items", "/items/{id}"), POLICY)

        @Bean
        fun things() = Things()
    }

    @RestController
    class Things {
        @GetMapping("/api/things")
        fun things() = mapOf("a" to 1)
    }

    private companion object {
        const val POLICY = "default-src 'self'; frame-ancestors 'none'"
    }
}
