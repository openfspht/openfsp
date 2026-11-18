package org.openfsp.validator

import com.sun.net.httpserver.HttpServer
import org.openfsp.protocol.ExchangeRedaction
import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.LevelVerdict
import org.openfsp.validator.engine.RunListener
import org.openfsp.validator.engine.ScenarioResult
import org.openfsp.validator.engine.Verdict
import org.openfsp.validator.engine.VersionRefusedException
import org.openfsp.validator.report.Reports
import org.openfsp.validator.reports.json.JsonReportFormat
import org.openfsp.validator.suite.Requirement
import org.openfsp.validator.suite.Scenario
import org.openfsp.validator.suites.SuiteFiles
import org.openfsp.validator.suite.TargetKind
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EngineTest {
    private var announcedVersion = "0.1.0"
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val authorization = exchange.requestHeaders.getFirst("Authorization").orEmpty()
                val (status, body) =
                    when (exchange.requestURI.path) {
                        "/.well-known/openfsp-configuration" -> 200 to """{"protocol_version":"$announcedVersion"}"""
                        "/v1/capabilities" -> 200 to """{"providers":[{"id":"mock_a","capabilities":["payments"]}]}"""
                        "/v1/things" -> 200 to """{"value":1,"items":[{"code":"a"},{"code":"b"}],"echo":"$authorization"}"""
                        else -> 404 to """{"type":"not-found"}"""
                    }
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
    private val directory: Path = Files.createTempDirectory("conformance")

    @AfterTest
    fun stop() {
        server.stop(0)
    }

    @Test
    fun `verdicts follow the scenario outcome and core fails on one failed core scenario`() {
        val suite = SuiteFiles.load(suite())
        val configuration = configuration()
        val run = engine(suite, configuration).run()
        val verdicts = run.results.associate { it.scenario.id to it.verdict }

        assertEquals(Verdict.PASS, verdicts["things/pass"])
        assertEquals(Verdict.FAIL, verdicts["things/fail"])
        assertEquals(Verdict.ASSISTED, verdicts["things/assisted"])
        assertEquals(Verdict.NOT_APPLICABLE, verdicts["things/profile"])
        assertEquals(Verdict.ASSISTED, verdicts["things/control"])

        val failure = assertNotNull(run.results.single { it.scenario.id == "things/fail" }.failure)
        assertEquals("equals /value", failure.mismatches.single().expectation)
        val exchange = assertNotNull(failure.exchange)
        assertFalse(exchange.response.body.contains(SECRET_KEY))
        assertEquals(ExchangeRedaction.MASK, exchange.request.headers["Authorization"])

        val report = Reports.of(suite, configuration, run)
        assertEquals(LevelVerdict.FAIL, report.core)
        assertEquals(Reports.NO_LEVEL, report.claim.level)
        assertTrue(report.limits.any { it.reference == "conformance#8.1" })
        assertFalse(JsonReportFormat.write(report).contains(SECRET_KEY))
    }

    @Test
    fun `an assisted scenario runs once its inputs are provided`() {
        val suite = SuiteFiles.load(suite())
        val run = engine(suite, configuration(inputs = mapOf("other-key" to "ofsp_test_other"))).run()
        assertEquals(Verdict.PASS, run.results.single { it.scenario.id == "things/assisted" }.verdict)
    }

    @Test
    fun `the listener hears each scenario start before it ends, in plan order`() {
        val suite = SuiteFiles.load(suite())
        val heard = mutableListOf<String>()
        val listener =
            object : RunListener {
                override fun started(
                    index: Int,
                    total: Int,
                    scenario: Scenario,
                    requirement: Requirement,
                ) {
                    heard += "start $index/$total ${scenario.id}"
                }

                override fun finished(
                    index: Int,
                    total: Int,
                    result: ScenarioResult,
                ) {
                    heard += "end $index/$total ${result.scenario.id}"
                }
            }
        val subject = engine(suite, configuration(), listener)
        val plan = subject.plan()
        subject.run()
        val expected = plan.mapIndexed { position, scenario -> listOf("start ${position + 1}/${plan.size} ${scenario.id}", "end ${position + 1}/${plan.size} ${scenario.id}") }.flatten()
        assertEquals(expected, heard)
    }

    @Test
    fun `another major protocol version is refused`() {
        announcedVersion = "1.0.0"
        val suite = SuiteFiles.load(suite())
        assertFailsWith<VersionRefusedException> { engine(suite, configuration()).run() }
    }

    private fun configuration(inputs: Map<String, String> = emptyMap()) =
        Configuration(
            target = TargetKind.SERVER,
            baseUrl = "http://127.0.0.1:${server.address.port}",
            apiKey = SECRET_KEY,
            inputs = inputs,
        )

    private fun suite(): Path {
        directory.resolve("requirements.yaml").writeText(
            """
            suite:
              version: 0.0.1
              protocol-version: 0.1.0
              discovery:
                path: /.well-known/openfsp-configuration
                protocol-version: /protocol_version
              capabilities:
                path: /v1/capabilities
                providers: /providers
                id: /id
                names: /capabilities
            limits:
              - reference: conformance#8.1
                text: limit
            requirements:
              - { id: T-1, requirement: things#1, level: core, targets: [server], title: one }
              - { id: T-2, requirement: things#2, level: payments.lookup, targets: [server], title: two }
            """.trimIndent(),
        )
        val scenarios = directory.resolve("scenarios/things").createDirectories()
        scenarios.resolve("pass.yaml").writeText(scenario("T-1", "equals: { /value: 1 }, matches: { /items/*/code: '^[ab]$' }"))
        scenarios.resolve("fail.yaml").writeText(scenario("T-1", "equals: { /value: 2 }"))
        scenarios.resolve("profile.yaml").writeText(scenario("T-2", "status: 200"))
        scenarios.resolve("assisted.yaml").writeText(
            "assisted: { action: provide another key, inputs: [other-key] }\n" + scenario("T-1", "status: 200", auth = "\"{{input.other-key}}\""),
        )
        scenarios.resolve("control.yaml").writeText(
            """
            requirement: T-1
            title: chooses an outcome
            steps:
              - control: { declare: { reference: o1, scenario: DECLINE } }
            """.trimIndent(),
        )
        return directory
    }

    private fun scenario(
        requirement: String,
        expectation: String,
        auth: String? = null,
    ): String =
        """
        requirement: $requirement
        title: reads things
        steps:
          - request: { method: GET, path: /v1/things${auth?.let { ", auth: $it" } ?: ""} }
            expect: { $expectation }
        """.trimIndent()

    private companion object {
        const val SECRET_KEY = "ofsp_test_SecretKeyThatMustNeverAppearInAReport"
    }
}
