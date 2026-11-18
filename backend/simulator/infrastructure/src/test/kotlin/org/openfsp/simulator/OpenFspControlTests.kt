package org.openfsp.simulator

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openfsp.simulator.control.web.ControlController
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import kotlin.test.assertEquals

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "openfsp.simulator.response-delay=0ms",
        "openfsp.simulator.settle-after=0ms",
        "openfsp.simulator.hang-duration=50ms",
        "server.address=127.0.0.1",
        "openfsp.simulator.http-callback-hosts=gateway",
    ],
)
@ActiveProfiles("conformance")
class OpenFspControlTests {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var proprietary: ControlController

    private val client by lazy { localClient(port) }

    @BeforeEach
    fun reset() = proprietary.reset()

    @Test
    fun `the preparation response announces a control interface of the same origin as the issuer`() {
        val issuer = URI(client.get().uri("/mock_openfsp/.well-known/openfsp-configuration").retrieve().body(Map::class.java)!!["issuer"] as String)
        val announced = URI(client.post().uri("/mock_openfsp/conformance").retrieve().body(Map::class.java)!!["control_endpoint"] as String)
        assertEquals("${issuer.path}/conformance/control", announced.path)
        assertEquals(listOf(issuer.scheme, issuer.host, issuer.port), listOf(announced.scheme, announced.host, announced.port))
    }

    @Test
    fun `a scenario set under a reference decides the payment created with it`() {
        assertEquals(204, declare("ctl-decline", "DECLINE").statusCode.value())
        val paymentId = create("ctl-decline")
        val state = settled("ctl-decline")
        assertEquals(paymentId, state["payment"])
        assertEquals("declined", read(paymentId)["failure_reason"])
    }

    @Test
    fun `an unknown scenario and one the operator cannot produce are refused with invalid-field`() {
        val unknown = declare("ctl-unknown", "NOT_A_SCENARIO")
        val unavailable = declare("ctl-unavailable", "UNKNOWN_STATUS", operator = "mock_no_source", key = NO_SOURCE_API_KEY)
        listOf(unknown, unavailable).forEach { refusal ->
            assertEquals(422, refusal.statusCode.value())
            assertEquals(MediaType.APPLICATION_PROBLEM_JSON, refusal.headers.contentType)
            assertEquals("https://openfsp.org/problems/invalid-field", refusal.body!!["type"])
        }
    }

    @Test
    fun `the state of a reference without a payment is not-found`() {
        val missing = client.get().uri("$CONTROL/references/ctl-missing").header(AUTHORIZATION, "Bearer $API_KEY").retrieve().toEntity(Map::class.java)
        assertEquals(404, missing.statusCode.value())
        assertEquals("https://openfsp.org/problems/not-found", missing.body!!["type"])
    }

    @Test
    fun `a pending payment takes the outcome and the reason it is given`() {
        declare("ctl-pending", "PENDING_FOREVER")
        val paymentId = create("ctl-pending")
        assertEquals(false, state("ctl-pending")["settled"])
        assertEquals(422, resolve(paymentId, mapOf("outcome" to "SUCCESSFUL")).statusCode.value())
        assertEquals(204, resolve(paymentId, mapOf("outcome" to "failed", "reason" to "insufficient_funds")).statusCode.value())
        assertEquals(true, state("ctl-pending")["settled"])
        val payment = read(paymentId)
        assertEquals("failed", payment["status"])
        assertEquals("insufficient_funds", payment["failure_reason"])
        assertEquals(404, resolve("pay_unknown", mapOf("outcome" to "succeeded")).statusCode.value())
    }

    @Test
    fun `the held event of a payment is delivered on request`() {
        declare("ctl-held", "NO_CALLBACK")
        val paymentId = create("ctl-held")
        assertEquals(0, settled("ctl-held")["callbacks_sent"])
        val delivered = client.post().uri("$CONTROL/payments/{id}/callback", paymentId).header(AUTHORIZATION, "Bearer $API_KEY").retrieve().toBodilessEntity()
        assertEquals(204, delivered.statusCode.value())
        assertEquals(1, state("ctl-held")["callbacks_sent"])
        val unknown = client.post().uri("$CONTROL/payments/pay_unknown/callback").header(AUTHORIZATION, "Bearer $API_KEY").retrieve().toBodilessEntity()
        assertEquals(404, unknown.statusCode.value())
    }

    @Test
    fun `the control interface answers only to the key of the preparation response`() {
        val anonymous = client.get().uri("$CONTROL/references/ctl-any").retrieve().toEntity(Map::class.java)
        val secondPrincipal = declare("ctl-any", "DECLINE", key = SECOND_PRINCIPAL_KEY)
        listOf(anonymous, secondPrincipal).forEach { refusal ->
            assertEquals(401, refusal.statusCode.value())
            assertEquals("https://openfsp.org/problems/unauthenticated", refusal.body!!["type"])
        }
        assertEquals(404, client.get().uri("/mock_moncash/conformance/control/references/ctl-any").retrieve().toBodilessEntity().statusCode.value())
    }

    private fun declare(
        reference: String,
        scenario: String,
        operator: String = "mock_openfsp",
        key: String = API_KEY,
    ): ResponseEntity<Map<*, *>> =
        client
            .put()
            .uri("/$operator/conformance/control/references/{reference}/scenario", reference)
            .header(AUTHORIZATION, "Bearer $key")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("scenario" to scenario))
            .retrieve()
            .toEntity(Map::class.java)

    private fun resolve(
        paymentId: String,
        body: Map<String, String>,
    ): ResponseEntity<Map<*, *>> =
        client
            .post()
            .uri("$CONTROL/payments/{id}/resolve", paymentId)
            .header(AUTHORIZATION, "Bearer $API_KEY")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toEntity(Map::class.java)

    private fun state(reference: String): Map<*, *> = client.get().uri("$CONTROL/references/{reference}", reference).header(AUTHORIZATION, "Bearer $API_KEY").retrieve().body(Map::class.java)!!

    private fun settled(reference: String): Map<*, *> {
        repeat(ATTEMPTS) { if (state(reference)["settled"] != true) Thread.sleep(PAUSE_MILLIS) }
        return state(reference).also { assertEquals(true, it["settled"]) }
    }

    private fun read(paymentId: String): Map<*, *> = client.get().uri("/mock_openfsp/v1/payments/{id}", paymentId).header(AUTHORIZATION, "Bearer $API_KEY").retrieve().body(Map::class.java)!!

    private fun create(reference: String): String =
        client
            .post()
            .uri("/mock_openfsp/v1/payments")
            .header(AUTHORIZATION, "Bearer $API_KEY")
            .header("Idempotency-Key", "key-$reference")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                mapOf(
                    "reference" to reference,
                    "amount" to mapOf("amount" to 125000, "currency" to "HTG"),
                    "provider" to "mock_openfsp",
                    "callback_url" to "http://127.0.0.1:$port/test/callbacks",
                    "payer" to mapOf("phone_number" to "+50934567890"),
                ),
            ).retrieve()
            .body(Map::class.java)!!["id"] as String

    private companion object {
        const val CONTROL = "/mock_openfsp/conformance/control"
        const val AUTHORIZATION = "Authorization"
        const val API_KEY = "ofsp_test_MockOpenFspKeyPublishedForTheSimulator"
        const val SECOND_PRINCIPAL_KEY = "ofsp_test_MockOpenFspSecondPrincipalKeyForTheSimulator"
        const val NO_SOURCE_API_KEY = "ofsp_test_MockNoSourceKeyPublishedForTheSimulator"
        const val ATTEMPTS = 100
        const val PAUSE_MILLIS = 20L
    }
}
