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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["openfsp.validator.behind-trusted-proxy=true"])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrentRunsTests {
    @LocalServerPort
    private var port: Int = 0

    private val json = JsonMapper.builder().build()
    private val api by lazy { Api(port) }

    @AfterAll
    fun stop() {
        target.close()
        simulator.close()
    }

    private fun prepare(client: String): Pair<String, String> {
        val prepared = api.post("/api/runs", """{"target":"gateway"}""", mapOf("X-Forwarded-For" to client))
        assertEquals(201, prepared.statusCode(), prepared.body())
        val body = json.readTree(prepared.body())
        return body.path("id").asString() to body.at("/simulator/space").asString()
    }

    private fun start(id: String) = api.post("/api/runs/$id/start", """{"base_url":"${target.url}","api_key":"ofsp_test_ConcurrentKey"}""")

    @Test
    fun `two runs at once each drive their own simulator space and both pass core, a third waits for a free slot`() {
        val gate = CountDownLatch(1)
        target.gate = gate
        try {
            val (first, firstSpace) = prepare("198.51.100.1")
            val (second, secondSpace) = prepare("198.51.100.2")
            val (third, _) = prepare("198.51.100.3")
            assertNotEquals(firstSpace, secondSpace)

            assertEquals(202, start(first).statusCode())
            assertEquals(202, start(second).statusCode())
            repeat(200) { if (target.slowRequests.get() < 2) Thread.sleep(25) }
            assertEquals(2, target.slowRequests.get())
            val busy = start(third)
            assertEquals(409, busy.statusCode(), busy.body())
            assertEquals("https://openfsp.org/problems/validator/busy", json.readTree(busy.body()).path("type").asString())

            gate.countDown()
            val summaries =
                Executors.newVirtualThreadPerTaskExecutor().use { pool ->
                    listOf(first, second).map { id -> pool.submit<String> { api.events("/api/runs/$id/events").last().second } }.map { it.get() }
                }
            summaries.forEach { assertEquals("PASS", json.readTree(it).path("core").asString(), it) }

            val declaration = "PUT /control/operators/mock_openfsp/default-scenario"
            assertEquals(listOf(declaration), simulator.spaceRequests(firstSpace))
            assertEquals(listOf(declaration), simulator.spaceRequests(secondSpace))
        } finally {
            gate.countDown()
            target.gate = null
        }
    }

    @Test
    fun `behind a trusted proxy the rate limit counts the forwarded client address`() {
        repeat(RUNS_PER_ADDRESS) { prepare("203.0.113.9") }
        val limited = api.post("/api/runs", """{"target":"gateway"}""", mapOf("X-Forwarded-For" to "203.0.113.9"))
        assertEquals(429, limited.statusCode(), limited.body())
        prepare("203.0.113.10")
    }

    companion object {
        private const val RUNS_PER_ADDRESS = 3
        private val target = FakeTarget()
        private val simulator = FakeSimulator()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("openfsp.validator.conformance") { TestSuite.concurrent.toString() }
            registry.add("openfsp.validator.runs-per-address") { RUNS_PER_ADDRESS.toString() }
            registry.add("openfsp.validator.concurrency") { "2" }
            registry.add("openfsp.validator.simulator.control-url") { simulator.url }
        }
    }
}
