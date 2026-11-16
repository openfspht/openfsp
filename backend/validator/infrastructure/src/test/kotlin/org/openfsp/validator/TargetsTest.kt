package org.openfsp.validator

import com.sun.net.httpserver.HttpServer
import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.Verdict
import org.openfsp.validator.report.Reports
import org.openfsp.validator.suite.CatalogueCheck
import org.openfsp.validator.suite.SuiteLoader
import org.openfsp.validator.suite.TargetKind
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TargetsTest {
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val headers = exchange.requestHeaders
                val body =
                    when (exchange.requestURI.path) {
                        "/v1/capabilities" -> """{"providers":[{"id":"mock_own","capabilities":["payments","payments.lookup"]}]}"""
                        else -> """{"order":"${headers.getFirst("X-Order").orEmpty()}","provider":"${headers.getFirst("X-Provider").orEmpty()}"}"""
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
        val suite = SuiteLoader.load(suite())
        val native = engine(suite, configuration(TargetKind.NATIVE_OPERATOR)).run().results.associate { it.scenario.id to it.verdict }
        val gateway = engine(suite, configuration(TargetKind.GATEWAY)).run().results.associate { it.scenario.id to it.verdict }

        assertEquals(Verdict.PASS, native["things/shared"])
        assertEquals(Verdict.FAIL, gateway["things/shared"])
        assertTrue("things/gateway-only" !in native && "things/scoped" !in native)
        assertTrue("things/gateway-only" in gateway && "things/scoped" in gateway)
    }

    @Test
    fun `a native operator stands for the scenario provider and receives the reference as its order id`() {
        val suite = SuiteLoader.load(suite())
        val result = engine(suite, configuration(TargetKind.NATIVE_OPERATOR)).run().results.single { it.scenario.id == "things/echo" }
        assertEquals(Verdict.PASS, result.verdict, result.failure.toString())
    }

    @Test
    fun `a scenario on the absence of a capability is not applicable where the capability is announced`() {
        val suite = SuiteLoader.load(suite())
        val verdicts = engine(suite, configuration(TargetKind.NATIVE_OPERATOR)).run().results.associate { it.scenario.id to it.verdict }
        assertEquals(Verdict.NOT_APPLICABLE, verdicts["things/without-lookup"])
        assertEquals(Verdict.PASS, verdicts["things/without-statement"])
    }

    @Test
    fun `a requirement takes the level set for the target`() {
        val suite = SuiteLoader.load(suite())
        val native = engine(suite, configuration(TargetKind.NATIVE_OPERATOR)).run()
        val gateway = engine(suite, configuration(TargetKind.GATEWAY)).run()

        val profiled = native.results.single { it.scenario.id == "things/profiled" }
        assertEquals("payments.lookup", profiled.requirement.level)
        assertEquals(Verdict.PASS, profiled.verdict)
        assertEquals(Verdict.NOT_APPLICABLE, gateway.results.single { it.scenario.id == "things/profiled" }.verdict)
        assertEquals(listOf("payments.lookup"), Reports.of(suite, configuration(TargetKind.NATIVE_OPERATOR), native).profiles.map { it.name })
    }

    @Test
    fun `a scenario that drives the simulator without a control URL is assisted and says what to do`() {
        val suite = SuiteLoader.load(suite())
        val configuration = configuration(TargetKind.NATIVE_OPERATOR)
        val run = engine(suite, configuration).run()
        val entry = Reports.of(suite, configuration, run).results.single { it.scenario == "things/controlled" }
        assertEquals(Verdict.ASSISTED, entry.verdict)
        assertTrue(entry.assisted)
        assertTrue(assertNotNull(entry.action).contains("--simulator-url"))
    }

    @Test
    fun `the catalogue check reports targets a scenario or a step cannot run on and targets left without a scenario`() {
        suite()
        directory.resolve("scenarios/things/stray.yaml").writeText(
            """
            requirement: T-2
            title: stray
            targets: [native-operator]
            steps:
              - request: { method: GET, path: /v1/echo }
                targets: [native-operator]
            """.trimIndent(),
        )
        directory.resolve("scenarios/things/levelled.yaml").writeText("targets: [gateway]\n" + scenario("T-4", "status: 200"))
        val problems = CatalogueCheck.problems(SuiteLoader.load(directory))
        assertTrue(problems.contains("scenario things/stray runs on native-operator, which is not a target of T-2"), problems.toString())
        assertTrue(problems.contains("scenario things/stray step 1 runs on native-operator, which is not a target of the scenario"), problems.toString())
        assertTrue(problems.contains("requirement T-4 has no scenario for target native-operator"), problems.toString())
        assertTrue(problems.contains("requirement T-4 sets a level for client, which is not one of its targets"), problems.toString())
    }

    private fun configuration(target: TargetKind) =
        Configuration(
            target = target,
            baseUrl = "http://127.0.0.1:${server.address.port}",
            apiKey = "ofsp_test_key",
            simulatorUrl = null,
            derivationKey = null,
            conformance = directory,
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
              journal-settle: 0ms
            limits:
              - reference: conformance#5.6
                text: limit
            requirements:
              - { id: T-1, requirement: things#1, level: core, targets: [gateway, native-operator], title: one }
              - { id: T-2, requirement: things#2, level: core, targets: [gateway], title: two }
              - { id: T-3, requirement: things#3, level: webhooks.emit, targets: [gateway, native-operator], levels: { native-operator: payments.lookup }, title: three }
              - { id: T-4, requirement: things#4, level: core, targets: [gateway, native-operator], levels: { client: payments.lookup }, title: four }
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
              - name: gateway only
                targets: [gateway]
                request: { method: GET, path: /v1/echo }
                expect: { status: 599 }
            """.trimIndent(),
        )
        scenarios.resolve("gateway-only.yaml").writeText(scenario("T-2", "status: 200"))
        scenarios.resolve("scoped.yaml").writeText("targets: [gateway]\n" + scenario("T-1", "status: 200"))
        scenarios.resolve("echo.yaml").writeText(
            """
            requirement: T-1
            title: echo
            provider: mock_gateway_side
            steps:
              - request: { method: GET, path: /v1/echo, headers: { X-Order: "{{order}}", X-Provider: "{{provider}}" } }
                expect: { status: 200, equals: { /order: "{{reference}}", /provider: mock_own } }
            """.trimIndent(),
        )
        scenarios.resolve("without-lookup.yaml").writeText("provider: mock_gateway_side\nwithout-capability: payments.lookup\n" + scenario("T-1", "status: 200"))
        scenarios.resolve("without-statement.yaml").writeText("provider: mock_gateway_side\nwithout-capability: payments.statement\n" + scenario("T-1", "status: 200"))
        scenarios.resolve("profiled.yaml").writeText("provider: mock_gateway_side\n" + scenario("T-3", "status: 200"))
        scenarios.resolve("controlled.yaml").writeText(
            """
            requirement: T-1
            title: controlled
            steps:
              - simulator: { reset: true }
            """.trimIndent(),
        )
        return directory
    }

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
