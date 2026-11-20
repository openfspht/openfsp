package org.openfsp.validator

import com.sun.net.httpserver.HttpServer
import org.openfsp.validator.engine.CatalogueCheck
import org.openfsp.validator.engine.Checks
import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.Durations
import org.openfsp.validator.engine.Pointers
import org.openfsp.validator.engine.Verdict
import org.openfsp.validator.report.Reports
import org.openfsp.validator.suite.TargetKind
import org.openfsp.validator.suites.SuiteFiles
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TargetsTest {
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val headers = exchange.requestHeaders
                val body =
                    when (exchange.requestURI.path) {
                        "/v1/capabilities" -> """{"providers":[{"id":"mock_redirect","capabilities":["payments","payments.lookup"],"payments":{"return_url_required":true}},{"id":"mock_basic","capabilities":["payments"]},{"id":"mock_full","capabilities":["payments","payments.lookup"]}]}"""
                        else -> """{"provider":"${headers.getFirst("X-Provider").orEmpty()}"}"""
                    }
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
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
    fun `each target plans the scenarios of its requirements and runs only the steps meant for it`() {
        val suite = SuiteFiles.load(suite())
        val server = engine(suite, configuration(TargetKind.SERVER)).run().results.associate { it.scenario.id to it.verdict }
        val client = engine(suite, configuration(TargetKind.CLIENT)).run().results.associate { it.scenario.id to it.verdict }

        assertEquals(Verdict.PASS, server["things/shared"])
        assertEquals(Verdict.FAIL, client["things/shared"])
        assertTrue("things/client-only" !in server && "things/server-only" !in client)
        assertTrue("things/client-only" in client && "things/server-only" in server)
    }

    @Test
    fun `the provider is the first qualifying one that requires no return URL, unless a qualifying one is pinned`() {
        val suite = SuiteFiles.load(suite())
        val verdicts = engine(suite, configuration(TargetKind.SERVER)).run().results.associate { it.scenario.id to it.verdict }
        assertEquals(Verdict.PASS, verdicts["things/first-provider"])
        assertEquals(Verdict.PASS, verdicts["things/with-lookup"])
        val pinned = engine(suite, configuration(TargetKind.SERVER).copy(provider = "mock_full")).run().results.associate { it.scenario.id to it.verdict }
        assertEquals(Verdict.FAIL, pinned["things/first-provider"])
        assertEquals(Verdict.PASS, pinned["things/without-lookup"])
    }

    @Test
    fun `a scenario on the absence of a capability is not applicable where every provider announces it`() {
        val suite = SuiteFiles.load(suite())
        val verdicts = engine(suite, configuration(TargetKind.SERVER)).run().results.associate { it.scenario.id to it.verdict }
        assertEquals(Verdict.PASS, verdicts["things/without-lookup"])
        assertEquals(Verdict.NOT_APPLICABLE, verdicts["things/without-payments"])
    }

    @Test
    fun `a requirement takes the level set for the target`() {
        val suite = SuiteFiles.load(suite())
        val client = engine(suite, configuration(TargetKind.CLIENT)).run()
        val server = engine(suite, configuration(TargetKind.SERVER)).run()

        val profiled = client.results.single { it.scenario.id == "things/profiled" }
        assertEquals("payments.lookup", profiled.requirement.level)
        assertEquals(Verdict.PASS, profiled.verdict)
        assertEquals(Verdict.NOT_APPLICABLE, server.results.single { it.scenario.id == "things/profiled" }.verdict)
        assertEquals(listOf("payments.lookup"), Reports.of(suite, configuration(TargetKind.CLIENT), client).profiles.map { it.name })
    }

    @Test
    fun `a scenario that chooses an outcome without a control interface fails and names the rule`() {
        val suite = SuiteFiles.load(suite())
        val configuration = configuration(TargetKind.SERVER)
        val run = engine(suite, configuration).run()
        val entry = Reports.of(suite, configuration, run).results.single { it.scenario == "things/controlled" }
        assertEquals(Verdict.FAIL, entry.verdict)
        assertFalse(entry.assisted)
        val reason = assertNotNull(entry.reason)
        assertTrue(reason.contains("offers no control interface") && "4.9.1" in reason, reason)
    }

    @Test
    fun `the catalogue check reports targets a scenario or a step cannot run on and targets left without a scenario`() {
        suite()
        directory.resolve("scenarios/things/stray.yaml").writeText(
            """
            requirement: T-2
            title: stray
            targets: [client]
            steps:
              - request: { method: GET, path: /v1/echo }
                targets: [client]
            """.trimIndent(),
        )
        directory.resolve("scenarios/things/levelled.yaml").writeText("targets: [server]\n" + scenario("T-4", "status: 200"))
        val problems = CatalogueCheck(Checks(Pointers()), Durations()).problems(SuiteFiles.load(directory))
        assertTrue(problems.contains("scenario things/stray runs on client, which is not a target of T-2"), problems.toString())
        assertTrue(problems.contains("scenario things/stray step 1 runs on client, which is not a target of the scenario"), problems.toString())
        assertTrue(problems.contains("requirement T-4 has no scenario for target client"), problems.toString())
        assertTrue(problems.contains("requirement T-4 sets a level for robot, which is not one of its targets"), problems.toString())
    }

    private fun configuration(target: TargetKind) =
        Configuration(
            target = target,
            baseUrl = "http://127.0.0.1:${server.address.port}",
            apiKey = "ofsp_test_key",
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
                return-url-required: /payments/return_url_required
            limits:
              - reference: conformance#8.1
                text: limit
            requirements:
              - { id: T-1, requirement: things#1, level: core, targets: [server, client], title: one }
              - { id: T-2, requirement: things#2, level: core, targets: [server], title: two }
              - { id: T-3, requirement: things#3, level: webhooks.emit, targets: [server, client], levels: { client: payments.lookup }, title: three }
              - { id: T-4, requirement: things#4, level: core, targets: [server, client], levels: { robot: payments.lookup }, title: four }
            """.trimIndent(),
        )
        val scenarios = directory.resolve("scenarios/things").createDirectories()
        scenarios.resolve("shared.yaml").writeText(
            """
            requirement: T-1
            title: shared
            steps:
              - request: { method: GET, path: /v1/echo }
                expect: { status: 200 }
              - name: client only
                targets: [client]
                request: { method: GET, path: /v1/echo }
                expect: { status: 599 }
            """.trimIndent(),
        )
        scenarios.resolve("server-only.yaml").writeText(scenario("T-2", "status: 200"))
        scenarios.resolve("client-only.yaml").writeText("targets: [client]\n" + scenario("T-1", "status: 200"))
        scenarios.resolve("first-provider.yaml").writeText(echo("T-1", "mock_basic"))
        scenarios.resolve("with-lookup.yaml").writeText("with-capabilities: [payments.lookup]\n" + echo("T-1", "mock_full"))
        scenarios.resolve("without-lookup.yaml").writeText("without-capabilities: [payments.lookup]\n" + echo("T-1", "mock_basic"))
        scenarios.resolve("without-payments.yaml").writeText("without-capabilities: [payments]\n" + scenario("T-1", "status: 200"))
        scenarios.resolve("profiled.yaml").writeText(scenario("T-3", "status: 200"))
        scenarios.resolve("controlled.yaml").writeText(
            """
            requirement: T-1
            title: controlled
            steps:
              - control: { declare: { reference: o1, scenario: DECLINE } }
            """.trimIndent(),
        )
        return directory
    }

    private fun echo(
        requirement: String,
        provider: String,
    ): String =
        """
        requirement: $requirement
        title: echoes the provider
        steps:
          - request: { method: GET, path: /v1/echo, headers: { X-Provider: "{{provider}}" } }
            expect: { status: 200, equals: { /provider: $provider } }
        """.trimIndent()

    private fun scenario(
        requirement: String,
        expectation: String,
    ): String =
        """
        requirement: $requirement
        title: reads things
        steps:
          - request: { method: GET, path: /v1/echo }
            expect: { $expectation }
        """.trimIndent()
}
