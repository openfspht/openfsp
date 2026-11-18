package org.openfsp.validator

import com.sun.net.httpserver.HttpServer
import org.openfsp.protocol.webhook.KeyStatus
import org.openfsp.protocol.webhook.WebhookJwk
import org.openfsp.protocol.webhook.WebhookSigner
import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.runs.http.LocalWebhookReceiver
import org.openfsp.validator.engine.Verdict
import org.openfsp.validator.suites.SuiteFiles
import org.openfsp.validator.suite.TargetKind
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.util.concurrent.Executors
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebhookStepsTest {
    private val json = JsonMapper.builder().build()
    private val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val signer = WebhookSigner(keys.private, KEY_ID)
    private val client = HttpClient.newHttpClient()
    private val sender = Executors.newVirtualThreadPerTaskExecutor()
    private val receiver = LocalWebhookReceiver("127.0.0.1", 0, null)
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = Executors.newVirtualThreadPerTaskExecutor()
            createContext("/") { exchange ->
                val (status, body) =
                    when (exchange.requestURI.path) {
                        "/.well-known/openfsp-configuration" -> 200 to configuration()
                        "/v1/capabilities" -> 200 to """{"providers":[]}"""
                        "/v1/emit" -> 202.also { sender.execute { emit(exchange.requestURI.query, tampered = false) } } to "{}"
                        "/v1/emit-tampered" -> 202.also { sender.execute { emit(exchange.requestURI.query, tampered = true) } } to "{}"
                        else -> 404 to "{}"
                    }
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
    private val directory: Path = Files.createTempDirectory("conformance")
    private val base get() = "http://127.0.0.1:${server.address.port}"

    @AfterTest
    fun stop() {
        server.stop(0)
        receiver.close()
        sender.shutdownNow()
    }

    @Test
    fun `the receiver records deliveries, replies as instructed, and the signature check verifies them with the published keys`() {
        val run = engine(SuiteFiles.load(suite()), Configuration(TargetKind.SERVER, base, "ofsp_test_key", null, null, webhookInbox = receiver.inbox)).run()
        val results = run.results.associate { it.scenario.id to it }
        val retried = results.getValue("webhooks/retried")
        assertEquals(Verdict.PASS, retried.verdict, retried.failure?.toString() ?: retried.reason)
        val tampered = results.getValue("webhooks/tampered")
        assertEquals(Verdict.FAIL, tampered.verdict)
        assertTrue(tampered.failure!!.mismatches.single().observed.contains("Content-Digest"))
        assertEquals(Verdict.FAIL, results.getValue("webhooks/silent").verdict)
        assertEquals(500, receiver.inbox.deliveries().first().status)
    }

    @Test
    fun `a webhook step is inconclusive without a receiver and fails when the receiver never got a delivery`() {
        val suite = SuiteFiles.load(suite())
        val unconfigured = engine(suite, Configuration(TargetKind.SERVER, base, "ofsp_test_key", null, null, only = "webhooks/silent")).run().results.single()
        assertEquals(Verdict.INCONCLUSIVE, unconfigured.verdict)
        assertTrue(unconfigured.reason!!.contains("no webhook receiver"))
        val unregistered = engine(suite, Configuration(TargetKind.SERVER, base, "ofsp_test_key", null, null, only = "webhooks/silent", webhookInbox = receiver.inbox)).run().results.single()
        assertEquals(Verdict.FAIL, unregistered.verdict)
        assertTrue(unregistered.failure!!.mismatches.any { it.observed.contains("no delivery during the whole run") })
    }

    private fun configuration(): String {
        val jwk = WebhookJwk.of(KEY_ID, keys.public, KeyStatus.ACTIVE)
        return json.writeValueAsString(
            mapOf(
                "protocol_version" to "0.1.0",
                "webhook_keys" to listOf(mapOf("kid" to jwk.kid, "kty" to jwk.kty, "crv" to jwk.crv, "alg" to jwk.alg, "use" to jwk.use, "x" to jwk.x, "openfsp_status" to jwk.openfspStatus)),
            ),
        )
    }

    private fun emit(
        query: String,
        tampered: Boolean,
    ) {
        val url = java.net.URLDecoder.decode(query.removePrefix("to="), Charsets.UTF_8)
        val event = """{"id":"evt_$tampered","type":"payment.created","sequence":1}""".toByteArray()
        repeat(ATTEMPTS) { attempt ->
            val headers = signer.sign(url, event, "evt_$tampered", "payment.created")
            val body = if (tampered) """{"id":"evt_true","type":"payment.succeeded","sequence":1}""".toByteArray() else event
            val request = HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.ofByteArray(body))
            headers.forEach { (name, value) -> if (name != "Content-Type") request.header(name, value) }
            request.header("Content-Type", "application/json").header("OpenFSP-Delivery-Attempt", "${attempt + 1}")
            val status = client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode()
            if (status in 200..299) return
            Thread.sleep(RETRY_MILLIS)
        }
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
        val scenarios = directory.resolve("scenarios/webhooks").createDirectories()
        scenarios.resolve("retried.yaml").writeText(
            """
            requirement: T-1
            title: retried
            steps:
              - { name: configuration, request: { method: GET, path: /.well-known/openfsp-configuration, auth: none } }
              - receiver: { reply: { where: { /id: evt_false }, status: 500, times: 1 } }
              - request: { method: POST, path: "/v1/emit?to={{webhook_url}}" }
                expect: { status: 202 }
              - name: first
                webhook: { method: POST, where: { /id: evt_false }, within: 5s, every: 20ms }
                expect:
                  headers: { OpenFSP-Delivery-Attempt: "1" }
                  header-equals-member: { OpenFSP-Event-Id: /id }
                  headers-match: { Signature-Input: '^sig1=\("@method".*;tag="openfsp-webhook"' }
              - check: { name: http-message-signature, from: configuration, pointer: /webhook_keys }
              - name: second
                webhook: { where: { /id: evt_false }, pick: 2, within: 5s, every: 20ms, after: first, not-sooner: 200ms }
                expect: { same-body-as: first, headers: { OpenFSP-Delivery-Attempt: "2" } }
              - check: { name: http-message-signature, from: configuration, pointer: /webhook_keys }
              - webhook: { where: { /id: evt_false }, count: 2, during: 300ms, every: 20ms }
              - webhook: { path: .*/elsewhere, count: 0 }
            """.trimIndent(),
        )
        scenarios.resolve("silent.yaml").writeText(
            """
            requirement: T-1
            title: silent
            steps:
              - webhook: { where: { /id: evt_never }, within: 100ms, every: 20ms }
            """.trimIndent(),
        )
        scenarios.resolve("tampered.yaml").writeText(
            """
            requirement: T-1
            title: tampered
            steps:
              - { name: configuration, request: { method: GET, path: /.well-known/openfsp-configuration, auth: none } }
              - request: { method: POST, path: "/v1/emit-tampered?to={{webhook_url}}" }
              - webhook: { where: { /id: evt_true }, within: 5s, every: 20ms }
              - check: { name: http-message-signature, from: configuration, pointer: /webhook_keys }
            """.trimIndent(),
        )
        return directory
    }

    private companion object {
        const val KEY_ID = "whk_test"
        const val ATTEMPTS = 2
        const val RETRY_MILLIS = 300L
    }
}
