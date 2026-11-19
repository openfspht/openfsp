package org.openfsp.validator.web

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InterfaceTests {
    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `the validator serves its interface on its routes with the security headers`() {
        val api = Api(port)
        listOf("/", "/runs/0b4f3c1e", "/reports/0b4f3c1e", "/attestations/att_1").forEach { path ->
            val page = api.get(path, "text/html")
            assertEquals(200, page.statusCode(), path)
            assertTrue(page.body().contains("<div id=\"root\">"), path)
            val headers = page.headers()
            assertTrue(headers.firstValue("Content-Security-Policy").get().contains("frame-ancestors 'none'"), path)
            assertEquals("nosniff", headers.firstValue("X-Content-Type-Options").get(), path)
            assertEquals("DENY", headers.firstValue("X-Frame-Options").get(), path)
            assertEquals("no-referrer", headers.firstValue("Referrer-Policy").get(), path)
            assertEquals("no-cache", headers.firstValue("Cache-Control").get(), path)
        }
        assertEquals(200, api.get("/assets/validator-test.js", "*/*").statusCode())
    }

    @Test
    fun `the interface never shadows the API, which keeps its problem 404`() {
        val api = Api(port)
        assertEquals(200, api.get("/api/suite").statusCode())
        assertEquals(200, api.get("/.well-known/openfsp-validator").statusCode())
        listOf("/api/unknown", "/api/runs/x/y/z", "/runs", "/nothing").forEach { path ->
            val response = api.get(path)
            assertEquals(404, response.statusCode(), path)
            assertTrue(response.headers().firstValue("Content-Type").get().startsWith("application/problem+json"), path)
        }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("openfsp.validator.conformance") { TestSuite.directory.toString() }
        }
    }
}
