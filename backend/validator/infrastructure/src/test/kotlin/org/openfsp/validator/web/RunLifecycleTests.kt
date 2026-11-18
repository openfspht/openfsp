package org.openfsp.validator.web

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.openfsp.validator.reports.jobs.ReportPurgeJob
import org.openfsp.validator.run.RunSessions
import org.openfsp.validator.run.RunWebhooks
import org.openfsp.validator.report.spi.ReportStore
import org.openfsp.validator.report.StoredReport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@ActiveProfiles("development")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RunLifecycleTests {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var reports: ReportStore

    @Autowired
    private lateinit var purge: ReportPurgeJob

    @Autowired
    private lateinit var sessions: RunSessions

    @Autowired
    private lateinit var webhooks: RunWebhooks

    private val json = JsonMapper.builder().build()
    private val api by lazy { Api(port) }

    @AfterAll
    fun stop() {
        target.close()
    }

    private fun prepare(target: String = "server"): JsonNode {
        val prepared = api.post("/api/runs", """{"target":"$target"}""")
        assertEquals(201, prepared.statusCode(), prepared.body())
        return json.readTree(prepared.body())
    }

    private fun start(
        id: String,
        body: String,
    ) = api.post("/api/runs/$id/start", body)

    @Test
    fun `a run streams each scenario start and end, then a summary, and keeps the report`() {
        val id = prepare().path("id").asString()
        val created = start(id, request(restrictedKey = RESTRICTED_KEY))
        assertEquals(202, created.statusCode(), created.body())
        assertEquals(id, json.readTree(created.body()).path("id").asString())
        assertEquals("/api/runs/$id", created.headers().firstValue("Location").orElseThrow())

        val events = api.events("/api/runs/$id/events")
        val names = events.map { it.first }
        assertEquals("run", names.first())
        assertEquals("summary", names.last())
        val plan = json.readTree(events.first().second)
        val total = plan.path("total").asInt()
        assertEquals(4, total)
        assertEquals(total, names.count { it == "scenario-started" })
        assertEquals(total, names.count { it == "scenario-finished" })
        events.filter { it.first.startsWith("scenario-") }.chunked(2).forEach { (started, finished) ->
            assertEquals("scenario-started", started.first)
            assertEquals("scenario-finished", finished.first)
            assertEquals(json.readTree(started.second).at("/scenario/index").asInt(), json.readTree(finished.second).path("index").asInt())
        }

        val results = events.filter { it.first == "scenario-finished" }.map { json.readTree(it.second).path("result") }
        val verdicts = results.associate { it.path("scenario").asString() to it.path("verdict").asString() }
        assertEquals(mapOf("slow/wait" to "PASS", "things/fail" to "FAIL", "things/pass" to "PASS", "things/restricted" to "PASS"), verdicts)
        val failure = results.single { it.path("scenario").asString() == "things/fail" }.path("failure")
        assertEquals("1", failure.at("/mismatches/0/observed").asString())
        assertEquals("[REDACTED]", failure.at("/exchange/request/headers/Authorization").asString())

        val summary = json.readTree(events.last().second)
        assertEquals("FAIL", summary.path("core").asString())
        assertTrue(summary.path("limits").items().any { it.path("reference").asString() == "conformance#8.1" })
        assertFalse(events.any { API_KEY in it.second || RESTRICTED_KEY in it.second })

        val report = api.get("/api/runs/$id")
        assertEquals(200, report.statusCode())
        assertEquals(report.body(), api.get("/api/runs/$id/report").body())
        val document = json.readTree(report.body())
        assertEquals("FAIL", document.path("core").asString())
        assertEquals(4, document.path("results").size())
        assertFalse(report.body().contains(API_KEY))
        assertNotNull(reports.find(UUID.fromString(id)))

        assertEquals(names, api.events("/api/runs/$id/events").map { it.first })
        Thread.sleep(5)
        sessions.forgetFinished()
        val replayed = api.events("/api/runs/$id/events")
        assertEquals(listOf("run") + List(total) { "scenario-finished" } + "summary", replayed.map { it.first })
        assertEquals(json.readTree(events.last().second), json.readTree(replayed.last().second))
    }

    @Test
    fun `a second run while one is in progress is refused as busy`() {
        val gate = CountDownLatch(1)
        target.gate = gate
        try {
            val id = prepare().path("id").asString()
            assertEquals(202, start(id, request()).statusCode())
            val waiting = prepare().path("id").asString()
            assertProblem(start(waiting, request()), 409, "busy")
            assertProblem(api.get("/api/runs/$id"), 409, "run-in-progress")
            assertProblem(start(id, request()), 409, "run-started")
            gate.countDown()
            assertEquals("summary", api.events("/api/runs/$id/events").last().first)
            assertEquals(202, generateSequence { start(waiting, request()).statusCode().also { if (it == 409) Thread.sleep(50) } }.take(100).first { it != 409 })
            assertEquals("summary", api.events("/api/runs/$waiting/events").last().first)
        } finally {
            gate.countDown()
            target.gate = null
        }
    }

    @Test
    fun `a live key is refused before anything runs`() {
        val id = prepare().path("id").asString()
        assertProblem(start(id, request(apiKey = "ofsp_live_SomethingThatIsNotATestKey")), 422, "test-key-required")
        assertProblem(start(id, request(restrictedKey = "ofsp_live_SomethingThatIsNotATestKey")), 422, "test-key-required")
        assertProblem(api.get("/api/runs/$id"), 409, "run-not-started")
    }

    @Test
    fun `malformed requests and unknown runs answer problem details`() {
        val unknownTarget = api.post("/api/runs", """{"target":"client"}""")
        assertProblem(unknownTarget, 400, "invalid-request")
        assertEquals(listOf("/target"), json.readTree(unknownTarget.body()).path("errors").items().map { it.path("pointer").asString() })
        val id = prepare().path("id").asString()
        val invalid = start(id, """{"target":"client","base_url":"ftp://x"}""")
        assertProblem(invalid, 400, "invalid-request")
        val pointers = json.readTree(invalid.body()).path("errors").items().map { it.path("pointer").asString() }.toSet()
        assertEquals(setOf("/target", "/base_url"), pointers)
        assertProblem(start(UUID.randomUUID().toString(), request()), 404, "not-found")
        assertProblem(api.get("/api/runs/${UUID.randomUUID()}"), 404, "not-found")
        assertProblem(api.get("/api/runs/${UUID.randomUUID()}/events", "text/event-stream"), 404, "not-found")
    }

    @Test
    fun `a prepared run names its start and webhook addresses and the suite describes what a server under test needs`() {
        val first = prepare()
        assertEquals("/api/runs/${first.path("id").asString()}/start", first.path("start").asString())
        assertEquals("https://validator.example/api/runs/${first.path("id").asString()}/webhooks", first.path("webhook_url").asString())
        assertTrue(first.path("start_before").asString().isNotEmpty())
        assertTrue(first.path("simulator").isMissingNode)
        val suite: JsonNode = json.readTree(api.get("/api/suite").body())
        assertEquals("ofsp_test_", suite.path("key_prefix").asString())
        assertEquals(1, suite.path("concurrency").asInt())
        assertEquals("restricted-key", suite.path("restricted_key_input").asString())
        assertEquals(listOf("server"), suite.path("targets").items().map { it.asString() })
    }

    @Test
    fun `a prepared run that has not started and an unknown run refuse webhooks`() {
        val id = prepare().path("id").asString()
        assertEquals(404, api.post("/api/runs/$id/webhooks", """{"id":"evt_1","type":"payment.created"}""", mapOf("OpenFSP-Event-Id" to "evt_1")).statusCode())
        assertEquals(emptyList(), webhooks.find(UUID.fromString(id))!!.deliveries())
        assertEquals(404, api.post("/api/runs/${UUID.randomUUID()}/webhooks", "{}").statusCode())
    }

    @Test
    fun `reports older than the retention are purged`() {
        val old = UUID.randomUUID()
        val recent = UUID.randomUUID()
        reports.save(stored(old, Instant.now().minus(91, ChronoUnit.DAYS)))
        reports.save(stored(recent, Instant.now().minus(89, ChronoUnit.DAYS)))
        purge.run()
        assertNull(reports.find(old))
        assertNotNull(reports.find(recent))
    }

    private fun stored(
        id: UUID,
        createdAt: Instant,
    ) = StoredReport(id, createdAt, "server", "http://example.test", "PASS", "0.0.1", "{}")

    private fun assertProblem(
        response: java.net.http.HttpResponse<String>,
        status: Int,
        slug: String,
    ) {
        assertEquals(status, response.statusCode(), response.body())
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").orElseThrow())
        assertEquals("https://openfsp.org/problems/validator/$slug", json.readTree(response.body()).path("type").asString())
    }

    private fun request(
        apiKey: String = API_KEY,
        restrictedKey: String? = null,
    ): String =
        json.writeValueAsString(
            mapOf("target" to "server", "base_url" to target.url, "api_key" to apiKey, "restricted_key" to restrictedKey).filterValues { it != null },
        )

    companion object {
        private const val API_KEY = "ofsp_test_KeyThatMustNeverAppearInAnEvent"
        private const val RESTRICTED_KEY = "ofsp_test_RestrictedKeyThatMustNeverAppear"
        private val target = FakeTarget()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("openfsp.validator.conformance") { TestSuite.directory.toString() }
            registry.add("openfsp.validator.runs-per-address") { "100" }
            registry.add("openfsp.validator.finished-run-memory") { "0s" }
            registry.add("openfsp.validator.concurrency") { "1" }
            registry.add("openfsp.validator.public-url") { "https://validator.example" }
        }
    }
}
