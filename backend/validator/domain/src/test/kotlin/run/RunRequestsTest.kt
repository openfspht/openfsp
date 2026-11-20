package org.openfsp.validator.run

import org.openfsp.validator.TestSuites
import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.TransportException
import org.openfsp.validator.engine.spi.Transport
import org.openfsp.validator.engine.spi.stubs.ScriptedTransport
import org.openfsp.validator.json.spi.stubs.PlainJsonCodec
import org.openfsp.validator.problem.ProblemKind
import org.openfsp.validator.problem.ValidatorException
import org.openfsp.validator.run.spi.stubs.DirectTargetConnections
import org.openfsp.validator.suite.TargetKind
import org.openfsp.validator.suite.spi.stubs.FixedSuiteSource
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RunRequestsTest {
    private val settings = TestSettings.run()
    private val run = PreparedRun(UUID.randomUUID(), TargetKind.SERVER, Instant.MAX)
    private val webhookUrl = "http://validator.test/api/runs/${run.id}/webhooks"

    private fun announcing(
        endpoint: String,
        answer: Pair<Int, String>,
    ) = ScriptedTransport(
        mapOf(
            "GET /.well-known/openfsp-configuration" to (200 to """{"protocol_version":"0.1.0","conformance_endpoint":"$endpoint"}"""),
            "POST /v1/conformance" to answer,
        ),
    )

    private fun connections(transport: ScriptedTransport) = DirectTargetConnections(transport)

    private fun requests(connections: DirectTargetConnections) = RunRequests(settings, FixedSuiteSource(TestSuites.things()), connections, ServerFixtures(PlainJsonCodec, RequiredMembers))

    private fun order(baseUrl: String = "https://target.test") = RunOrder(baseUrl = baseUrl)

    @Test
    fun `a server that announces a conformance endpoint provides its own test key`() {
        val transport = announcing("https://target.test/v1/conformance", 201 to """{"api_key":"ofsp_test_issued","expires_at":"2026-11-18T12:00:00Z","control_endpoint":"https://target.test/v1/conformance/control","inputs":{"unknown":"x"}}""")
        val requested = requests(connections(transport)).configuration(order().copy(accessCode = "letmein"), run, webhookUrl)
        assertEquals("ofsp_test_issued", requested.configuration.apiKey)
        assertEquals(Configuration.SERVER, requested.configuration.preparedBy)
        assertTrue(requested.configuration.inputs.isEmpty())
        val asked = transport.requests.last()
        assertEquals("Bearer letmein", asked.headers["Authorization"])
        assertTrue(String(asked.body!!).contains(webhookUrl))
    }

    @Test
    fun `a control interface is kept only when it shares the origin of the server`() {
        val issued = { control: String -> announcing("https://target.test/v1/conformance", 201 to """{"api_key":"ofsp_test_issued","expires_at":"2026-11-18T12:00:00Z","control_endpoint":"$control"}""") }
        val same = requests(connections(issued("https://target.test/v1/conformance/control/"))).configuration(order(), run, webhookUrl)
        assertEquals("https://target.test/v1/conformance/control", same.configuration.controlEndpoint)
        assertEquals("suite", same.configuration.redacted("0.0.1")["outcomes"])
        val elsewhere = assertFailsWith<ValidatorException> { requests(connections(issued("https://elsewhere.test/control"))).configuration(order(), run, webhookUrl) }
        assertEquals(ProblemKind.PREPARATION_FAILED, elsewhere.kind)
    }

    @Test
    fun `a preparation answer that does not follow the fixtures schema fails the preparation`() {
        val incomplete = announcing("https://target.test/v1/conformance", 201 to """{"api_key":"ofsp_test_issued","expires_at":"2026-11-18T12:00:00Z"}""")
        val refused = assertFailsWith<ValidatorException> { requests(connections(incomplete)).configuration(order(), run, webhookUrl) }
        assertEquals(ProblemKind.PREPARATION_FAILED, refused.kind)
        assertTrue(refused.message!!.contains("control_endpoint"), refused.message)
    }

    @Test
    fun `a key given by the operator is used without asking the server`() {
        val transport = announcing("https://target.test/v1/conformance", 201 to """{"api_key":"ofsp_test_issued"}""")
        val requested = requests(connections(transport)).configuration(order().copy(apiKey = "ofsp_test_given", controlEndpoint = "https://target.test/control/"), run, webhookUrl)
        assertEquals("ofsp_test_given", requested.configuration.apiKey)
        assertEquals(Configuration.OPERATOR, requested.configuration.preparedBy)
        assertEquals("https://target.test/control", requested.configuration.controlEndpoint)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `a key given by the operator needs the control interface of the server on its origin`() {
        val transport = announcing("https://target.test/v1/conformance", 201 to """{"api_key":"ofsp_test_issued"}""")
        listOf(null, "https://elsewhere.test/control").forEach { control ->
            val refused = assertFailsWith<ValidatorException> { requests(connections(transport)).configuration(order().copy(apiKey = "ofsp_test_given", controlEndpoint = control), run, webhookUrl) }
            assertEquals(ProblemKind.INVALID_REQUEST, refused.kind, control)
            assertEquals(listOf("/control_endpoint"), refused.errors.map { it.pointer }, control)
        }
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `a server without a conformance endpoint needs a key and releases its connection`() {
        val connections = connections(TestSuites.target())
        val refused = assertFailsWith<ValidatorException> { requests(connections).configuration(order(), run, webhookUrl) }
        assertEquals(ProblemKind.CREDENTIALS_REQUIRED, refused.kind)
        assertEquals(1, connections.closed.get())
    }

    @Test
    fun `an endpoint on another origin is ignored`() {
        val transport = announcing("https://elsewhere.test/v1/conformance", 201 to """{"api_key":"ofsp_test_issued"}""")
        val refused = assertFailsWith<ValidatorException> { requests(connections(transport)).configuration(order(), run, webhookUrl) }
        assertEquals(ProblemKind.CREDENTIALS_REQUIRED, refused.kind)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `a refused access code and a live key are reported as such`() {
        val guarded = announcing("https://target.test/v1/conformance", 401 to """{"type":"unauthenticated"}""")
        assertEquals(ProblemKind.ACCESS_CODE_REQUIRED, assertFailsWith<ValidatorException> { requests(connections(guarded)).configuration(order(), run, webhookUrl) }.kind)
        val live = announcing("https://target.test/v1/conformance", 201 to """{"api_key":"ofsp_live_issued","expires_at":"2026-11-18T12:00:00Z","control_endpoint":"https://target.test/c"}""")
        assertEquals(ProblemKind.PREPARATION_FAILED, assertFailsWith<ValidatorException> { requests(connections(live)).configuration(order(), run, webhookUrl) }.kind)
        val broken = announcing("https://target.test/v1/conformance", 500 to "{}")
        assertEquals(ProblemKind.PREPARATION_FAILED, assertFailsWith<ValidatorException> { requests(connections(broken)).configuration(order(), run, webhookUrl) }.kind)
    }

    @Test
    fun `a server that cannot be reached is reported as unreachable with the failed request`() {
        val refusing = Transport { request -> throw TransportException("${request.method} ${request.url} failed: Connection refused", IOException("refused")) }
        val connections = DirectTargetConnections(refusing)
        val refused = assertFailsWith<ValidatorException> { requests(connections).configuration(order(), run, webhookUrl) }
        assertEquals(ProblemKind.UNREACHABLE, refused.kind)
        assertTrue(refused.detail!!.contains("Connection refused"), refused.detail)
        assertEquals(1, connections.closed.get())
    }
}
