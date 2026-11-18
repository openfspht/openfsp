package org.openfsp.simulator

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openfsp.protocol.webhook.InMemoryNonceStore
import org.openfsp.protocol.webhook.Verification
import org.openfsp.protocol.webhook.WebhookJwk
import org.openfsp.protocol.webhook.WebhookVerifier
import org.openfsp.simulator.control.web.ControlController
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "openfsp.simulator.response-delay=0ms",
        "openfsp.simulator.settle-after=0ms",
        "server.address=127.0.0.1",
    ],
)
@ActiveProfiles("conformance")
class OpenFspEmissionTests {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var control: ControlController

    @Autowired
    private lateinit var recorder: CallbackRecorder

    @Autowired
    private lateinit var json: JsonMapper

    private val client by lazy { localClient(port) }

    private val webhookUrl get() = "http://127.0.0.1:$port/test/callbacks"

    @BeforeEach
    fun reset() {
        control.reset()
        recorder.received.clear()
    }

    @Test
    fun `a payment created with a run key delivers its creation and its success to the run webhook URL as signed events 1 and 2`() {
        val runKey = prepare()["api_key"] as String
        val payment = create(runKey, "emit1").body!!

        repeat(100) { if (recorder.received.size < 2) Thread.sleep(20) }
        val events = recorder.received.map { it to json.readValue(it.body, Map::class.java) }.sortedBy { (it.second["sequence"] as Number).toInt() }
        assertEquals(listOf("payment.created", "payment.succeeded"), events.map { it.second["type"] })
        assertEquals(listOf(1, 2), events.map { (it.second["sequence"] as Number).toInt() })
        events.forEach { (delivery, event) ->
            assertEquals(payment["id"], event["resource_id"])
            assertEquals("1", delivery.headers.getFirst("OpenFSP-Delivery-Attempt"))
            assertEquals(event["id"], delivery.headers.getFirst("OpenFSP-Event-Id"))
            assertIs<Verification.Verified>(verifier().verify("POST", webhookUrl, { delivery.headers.getFirst(it) }, delivery.body))
        }
        assertNull(events.first().second["previous_status"])
        assertEquals("pending", (events.first().second["data"] as Map<*, *>)["status"])
        assertEquals("pending", events.last().second["previous_status"])
    }

    @Test
    fun `a payment created with the configured key or refused at creation delivers nothing to the run webhook URL`() {
        prepare()
        create(API_KEY, "emit2")
        create(prepare()["api_key"] as String, "emit3", amount = 0)

        Thread.sleep(300)
        assertEquals(0, recorder.received.size)
    }

    @Test
    fun `a preparation refuses a webhook URL that is not an absolute http URL`() {
        val refused = client.post().uri("/mock_openfsp/conformance").contentType(MediaType.APPLICATION_JSON).body(mapOf("webhook_url" to "ftp://receiver")).retrieve().toEntity(Map::class.java)

        assertEquals(422, refused.statusCode.value())
        assertTrue(client.post().uri("/mock_openfsp/conformance").retrieve().toEntity(Map::class.java).statusCode.is2xxSuccessful)
    }

    private fun prepare(): Map<*, *> =
        client
            .post()
            .uri("/mock_openfsp/conformance")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("webhook_url" to webhookUrl))
            .retrieve()
            .body(Map::class.java)!!

    private fun create(
        key: String,
        reference: String,
        amount: Long = 125000,
    ): ResponseEntity<Map<*, *>> =
        client
            .post()
            .uri("/mock_openfsp/v1/payments")
            .header("Authorization", "Bearer $key")
            .header("Idempotency-Key", "key-$reference")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("reference" to reference, "amount" to mapOf("amount" to amount, "currency" to "HTG"), "provider" to "mock_openfsp"))
            .retrieve()
            .toEntity(Map::class.java)

    private fun verifier(): WebhookVerifier {
        val configuration = client.get().uri("/mock_openfsp/.well-known/openfsp-configuration").retrieve().body(Map::class.java)!!
        val keys = (configuration["webhook_keys"] as List<*>).map { json.convertValue(it, WebhookJwk::class.java) }.associate { it.kid to it.publicKeyOrNull() }
        return WebhookVerifier(keys = { keys[it] }, nonces = InMemoryNonceStore())
    }

    private companion object {
        const val API_KEY = "ofsp_test_MockOpenFspKeyPublishedForTheSimulator"
    }
}
