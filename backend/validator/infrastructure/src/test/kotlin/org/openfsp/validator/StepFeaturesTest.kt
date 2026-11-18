package org.openfsp.validator

import com.sun.net.httpserver.HttpServer
import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.Exchange
import org.openfsp.validator.engine.Expectations
import org.openfsp.validator.engine.OutgoingRequest
import org.openfsp.validator.engine.Variables
import org.openfsp.validator.engine.Verdict
import org.openfsp.validator.suite.Expectation
import org.openfsp.validator.suites.SuiteFiles
import org.openfsp.validator.suite.TargetKind
import org.openfsp.validator.json.JsonNumber
import org.openfsp.validator.json.JsonString
import org.openfsp.validator.shared.json.JacksonJsonCodec
import org.openfsp.validator.suites.NetworkntSchemas
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StepFeaturesTest {
    private val inFlight = AtomicInteger()
    private val overlapped = AtomicInteger()
    private val paymentReads = AtomicInteger()
    private val controlCalls = CopyOnWriteArrayList<String>()
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()
            createContext("/") { exchange ->
                val (status, body) =
                    when (exchange.requestURI.path) {
                        "/.well-known/openfsp-configuration" -> 200 to """{"protocol_version":"0.1.0"}"""
                        "/v1/capabilities" -> 200 to """{"providers":[]}"""
                        "/v1/slow" -> {
                            if (inFlight.incrementAndGet() > 1) overlapped.incrementAndGet()
                            Thread.sleep(SLOW_MILLIS)
                            inFlight.decrementAndGet()
                            200 to """{"providers":[{"id":"a","limit":{"amount":1000}},{"id":"b","limit":{"amount":2000}}]}"""
                        }
                        "$CONTROL/references/o1" -> 200 to """{"payment":"MO-1","settled":${paymentReads.incrementAndGet() >= 3},"callbacks_sent":1}"""
                        else ->
                            if (exchange.requestURI.path.startsWith("$CONTROL/")) {
                                controlCalls += "${exchange.requestMethod} ${exchange.requestURI.path} ${String(exchange.requestBody.readAllBytes())} ${exchange.requestHeaders.getFirst("Authorization")}".replace("  ", " ")
                                (if (exchange.requestURI.path.endsWith("/o3/scenario")) 422 else 204) to ""
                            } else {
                                404 to "{}"
                            }
                    }
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
    private val directory: Path = Files.createTempDirectory("conformance")
    private val base get() = "http://127.0.0.1:${server.address.port}"

    @AfterTest
    fun stop() {
        server.stop(0)
    }

    @Test
    fun `templates add to and subtract from an integer and name today in a zone`() {
        val variables = Variables("run", 1, emptyMap(), emptyMap(), JacksonJsonCodec)
        variables.capture("min", JsonNumber.of(1000))
        assertEquals(JsonNumber.of(999), variables.resolve(JsonString("{{min - 1}}")))
        assertEquals("amount 1001", variables.resolve("amount {{ min + 1 }}"))
        assertEquals(LocalDate.now(ZoneId.of("America/Port-au-Prince")).toString(), variables.resolve("{{today:America/Port-au-Prince}}"))
    }

    @Test
    fun `any of passes when one alternative holds and reports every alternative otherwise`() {
        val expectations = Expectations({ NetworkntSchemas(emptyMap()) }, JacksonJsonCodec)
        val exchange = Exchange(OutgoingRequest("GET", base, emptyMap(), null), 409, mapOf("Retry-After" to listOf("1")), """{"type":"busy"}""".toByteArray())
        val variables = Variables("run", 1, emptyMap(), emptyMap(), JacksonJsonCodec)
        val waited = Expectation(status = 201)
        val refused = Expectation(status = 409, headersPresent = listOf("Retry-After"))
        assertTrue(expectations.evaluate(Expectation(anyOf = listOf(waited, refused)), exchange, variables) { null }.isEmpty())
        val failed = expectations.evaluate(Expectation(anyOf = listOf(waited, Expectation(status = 422))), exchange, variables) { null }
        assertEquals("any of 2 alternatives", failed.single().expectation)
    }

    @Test
    fun `parallel requests overlap, filters select by member and the control interface settles, resolves and calls back a payment`() {
        val suite = SuiteFiles.load(suite())
        val run = engine(suite, Configuration(TargetKind.SERVER, base, "ofsp_test_key", base + CONTROL)).run()
        val results = run.results.associate { it.scenario.id to it }
        assertEquals(Verdict.PASS, results.getValue("steps/parallel").verdict, results.getValue("steps/parallel").failure.toString())
        assertTrue(overlapped.get() > 0)
        assertEquals(Verdict.PASS, results.getValue("steps/settled").verdict, results.getValue("steps/settled").reason)
        assertTrue(paymentReads.get() >= 3)
        assertEquals(
            listOf(
                "PUT $CONTROL/references/o2/scenario {\"scenario\":\"DECLINE\"} Bearer ofsp_test_key",
                "POST $CONTROL/payments/MO-1/resolve {\"outcome\":\"failed\",\"reason\":\"declined\"} Bearer ofsp_test_key",
                "POST $CONTROL/payments/MO-1/callback Bearer ofsp_test_key",
            ),
            controlCalls.filterNot { it.contains("/o3/") },
        )
        assertEquals(Verdict.ASSISTED, results.getValue("steps/unavailable").verdict)
        assertEquals(Verdict.INCONCLUSIVE, results.getValue("steps/unsettled").verdict)
    }

    private fun suite(): Path {
        directory.resolve("requirements.yaml").writeText(
            """
            suite:
              version: 0.0.1
              protocol-version: 0.1.0
              discovery: { path: /.well-known/openfsp-configuration, protocol-version: /protocol_version }
              capabilities: { path: /v1/capabilities, providers: /providers, id: /id, names: /capabilities }
            limits:
              - { reference: conformance#8.1, text: limit }
            requirements:
              - { id: T-1, requirement: things#1, level: core, targets: [server], title: one }
            """.trimIndent(),
        )
        val scenarios = directory.resolve("scenarios/steps").createDirectories()
        scenarios.resolve("parallel.yaml").writeText(
            """
            requirement: T-1
            title: parallel
            variables: { wanted: b }
            steps:
              - parallel:
                  - { name: first, request: { method: GET, path: /v1/slow }, expect: { status: 200 }, capture: { limit: "/providers/[id={{wanted}}]/limit/amount" } }
                  - { name: second, request: { method: GET, path: /v1/slow }, expect: { status: 200, equals: { "/providers/[id=a]/limit/amount": 1000 } } }
              - request: { method: GET, path: /v1/slow }
                expect: { equals: { "/providers/[id={{wanted}}]/limit/amount": "{{limit}}", "/providers/[id=a]/limit/amount": "{{limit - 1000}}" } }
            """.trimIndent(),
        )
        scenarios.resolve("settled.yaml").writeText(
            """
            requirement: T-1
            title: settled
            steps:
              - control: { settled: { reference: o1, callbacks: 1, within: 2s, every: 10ms } }
              - control: { declare: { reference: o2, scenario: DECLINE } }
              - control: { resolve: { reference: o1, outcome: failed, reason: declined } }
              - control: { callback: { reference: o1 } }
            """.trimIndent(),
        )
        scenarios.resolve("unsettled.yaml").writeText(
            """
            requirement: T-1
            title: unsettled
            steps:
              - control: { settled: { reference: o1, callbacks: 2, within: 100ms, every: 10ms } }
            """.trimIndent(),
        )
        scenarios.resolve("unavailable.yaml").writeText(
            """
            requirement: T-1
            title: unavailable
            steps:
              - control: { declare: { reference: o3, scenario: UNKNOWN_STATUS } }
            """.trimIndent(),
        )
        return directory
    }

    private companion object {
        const val SLOW_MILLIS = 300L
        const val CONTROL = "/conformance/control"
    }
}
