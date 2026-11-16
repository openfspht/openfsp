package org.openfsp.validator.web

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AddressGuardTests {
    @LocalServerPort
    private var port: Int = 0

    private val json = JsonMapper.builder().build()

    @AfterAll
    fun stop() = simulator.close()

    @Test
    fun `outside development, loopback, private, link-local and metadata targets are refused`() {
        val api = Api(port)
        val id = json.readTree(api.post("/api/runs", """{"target":"server"}""").body()).path("id").asString()
        listOf("http://127.0.0.1:8080", "http://localhost:8080", "http://10.0.0.5", "http://192.168.1.10", "http://[fe80::1]", "http://169.254.169.254", "http://[::ffff:10.0.0.1]").forEach { url ->
            val response = api.post("/api/runs/$id/start", """{"base_url":"$url","api_key":"ofsp_test_Key"}""")
            assertEquals(422, response.statusCode(), "$url: ${response.body()}")
            assertEquals("https://openfsp.org/problems/validator/private-address", json.readTree(response.body()).path("type").asString(), url)
        }
    }

    companion object {
        private val simulator = FakeSimulator()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("openfsp.validator.conformance") { TestSuite.directory.toString() }
            registry.add("openfsp.validator.development-mode") { "false" }
            registry.add("openfsp.validator.simulator.control-url") { simulator.url }
        }
    }
}
