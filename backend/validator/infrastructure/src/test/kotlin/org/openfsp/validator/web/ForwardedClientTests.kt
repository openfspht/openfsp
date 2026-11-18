package org.openfsp.validator.web

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
@ActiveProfiles("development")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["openfsp.validator.behind-trusted-proxy=true", "openfsp.validator.runs-per-address=2"],
)
class ForwardedClientTests {
    @LocalServerPort
    private var port: Int = 0

    private val api by lazy { Api(port) }

    private fun prepare(forwardedFor: String) = api.post("/api/runs", """{"target":"server"}""", mapOf("X-Forwarded-For" to forwardedFor)).statusCode()

    @Test
    fun `the client address is the last one the trusted proxy appended, not a value the client wrote`() {
        assertEquals(201, prepare("203.0.113.1, 198.51.100.9"))
        assertEquals(201, prepare("203.0.113.2, 198.51.100.9"))
        assertEquals(429, prepare("203.0.113.3, 198.51.100.9"))
        assertEquals(201, prepare("198.51.100.9, 198.51.100.10"))
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("openfsp.validator.conformance") { TestSuite.directory.toString() }
        }
    }
}
