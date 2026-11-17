package org.openfsp.validator.run

import org.openfsp.validator.TestSuites
import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.spi.stubs.ScriptedTransport
import org.openfsp.validator.json.spi.stubs.PlainJsonCodec
import org.openfsp.validator.problem.ProblemKind
import org.openfsp.validator.problem.ValidatorException
import org.openfsp.validator.run.spi.SimulatorSpace
import org.openfsp.validator.run.spi.stubs.DirectTargetConnections
import org.openfsp.validator.suite.TargetKind
import org.openfsp.validator.suite.spi.stubs.FixedSuiteSource
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RunRequestsTest {
    private val settings = TestSettings.run()
    private val space = SimulatorSpace("s", "http://simulator.test/runs/s", "http://simulator.test/runs/s")
    private val run = PreparedRun(UUID.randomUUID(), TargetKind.SERVER, space, Instant.EPOCH, Instant.MAX)
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

    private fun requests(connections: DirectTargetConnections) = RunRequests(settings, FixedSuiteSource(TestSuites.things()), connections, ServerFixtures(PlainJsonCodec))

    private fun order(baseUrl: String = "https://target.test") = RunOrder(baseUrl = baseUrl)

    @Test
    fun `the reference server of the run space needs no key`() {
        val transport = TestSuites.target()
        val requested = requests(connections(transport)).configuration(order("${space.publicUrl}/mock_openfsp"), run, webhookUrl)
        assertEquals("ofsp_test_reference", requested.configuration.apiKey)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `a server that announces a conformance endpoint provides its own test key`() {
        val transport = announcing("https://target.test/v1/conformance", 201 to """{"api_key":"ofsp_test_issued","inputs":{"unknown":"x"}}""")
        val requested = requests(connections(transport)).configuration(order().copy(accessCode = "letmein"), run, webhookUrl)
        assertEquals("ofsp_test_issued", requested.configuration.apiKey)
        assertEquals(Configuration.SERVER, requested.configuration.preparedBy)
        assertTrue(requested.configuration.inputs.isEmpty())
        val asked = transport.requests.last()
        assertEquals("Bearer letmein", asked.headers["Authorization"])
        assertTrue(String(asked.body!!).contains(webhookUrl))
    }

    @Test
    fun `a key given by the operator is used without asking the server`() {
        val transport = announcing("https://target.test/v1/conformance", 201 to """{"api_key":"ofsp_test_issued"}""")
        val requested = requests(connections(transport)).configuration(order().copy(apiKey = "ofsp_test_given"), run, webhookUrl)
        assertEquals("ofsp_test_given", requested.configuration.apiKey)
        assertEquals(Configuration.OPERATOR, requested.configuration.preparedBy)
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
        val live = announcing("https://target.test/v1/conformance", 201 to """{"api_key":"ofsp_live_issued"}""")
        assertEquals(ProblemKind.TEST_KEY_REQUIRED, assertFailsWith<ValidatorException> { requests(connections(live)).configuration(order(), run, webhookUrl) }.kind)
        val broken = announcing("https://target.test/v1/conformance", 500 to "{}")
        assertEquals(ProblemKind.PREPARATION_FAILED, assertFailsWith<ValidatorException> { requests(connections(broken)).configuration(order(), run, webhookUrl) }.kind)
    }
}
