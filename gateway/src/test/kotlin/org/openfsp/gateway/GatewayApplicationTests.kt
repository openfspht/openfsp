package org.openfsp.gateway

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayApplicationTests {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var json: JsonMapper

    @Autowired
    private lateinit var jdbc: JdbcClient

    private val client by lazy {
        RestClient
            .builder()
            .baseUrl("http://127.0.0.1:$port")
            .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
            .build()
    }

    @BeforeEach
    fun resetOperator() {
        operator.failWith = null
    }

    private fun body(reference: String = "INV-${UUID.randomUUID()}") =
        """{"reference":"$reference","amount":{"amount":125000,"currency":"HTG"},"provider":"mock_alpha","payer":{"phone_number":"+50934567890"}}"""

    private fun create(
        body: String,
        key: String? = UUID.randomUUID().toString(),
    ): ResponseEntity<ByteArray> =
        client
            .post()
            .uri("/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .headers { if (key != null) it.set("Idempotency-Key", key) }
            .body(body)
            .retrieve()
            .toEntity(ByteArray::class.java)

    private fun ResponseEntity<ByteArray>.json(): JsonNode = json.readTree(body)

    private fun ResponseEntity<ByteArray>.problem(): String = json().get("type").asString().substringAfterLast('/')

    private fun read(id: String): JsonNode = json.readTree(client.get().uri("/v1/payments/{id}", id).retrieve().body(ByteArray::class.java))

    private fun deliver(
        callback: Pair<String, String>,
        signature: String = callback.second,
    ) = client
        .post()
        .uri("/callbacks/mock_alpha")
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Alpha-Signature", signature)
        .body(callback.first)
        .retrieve()
        .toBodilessEntity()
        .statusCode
        .value()

    private fun conflicts(id: String) = jdbc.sql("select count(*) from payment_conflicts where payment_id = ?").param(id).query(Int::class.java).single()

    @Test
    fun `creates a pending payment with the next action required by the operator`() {
        val response = create(body())
        assertEquals(201, response.statusCode.value())
        val payment = response.json()
        assertEquals("pending", payment.get("status").asString())
        assertEquals("payer_approval", payment.get("next_action").get("type").asString())
        assertEquals("/v1/payments/${payment.get("id").asString()}", response.headers.location.toString())
    }

    @Test
    fun `replays the recorded response byte for byte`() {
        val key = UUID.randomUUID().toString()
        val body = body()
        val first = create(body, key)
        val calls = operator.calls.get()
        val replay = create(body, key)
        assertEquals("true", replay.headers.getFirst("Idempotent-Replay"))
        assertContentEquals(first.body, replay.body)
        assertEquals(calls, operator.calls.get())
    }

    @Test
    fun `rejects a reused key with another body and a missing key`() {
        val key = UUID.randomUUID().toString()
        create(body(), key)
        assertEquals("idempotency-key-reused", create(body(), key).problem())
        assertEquals("idempotency-key-required", create(body(), key = null).problem())
    }

    @Test
    fun `rejects a reference used by another request`() {
        val body = body()
        create(body)
        val second = create(body)
        assertEquals(409, second.statusCode.value())
        assertEquals("reference-conflict", second.problem())
    }

    @Test
    fun `a signed callback settles the payment once and a contradiction is recorded`() {
        val payment = create(body()).json()
        val id = payment.get("id").asString()
        val orderId = payment.get("provider_order_id").asString()

        assertEquals(204, deliver(operator.callback(orderId, "SUCCESSFUL")))
        assertEquals("succeeded", read(id).get("status").asString())
        assertEquals(204, deliver(operator.callback(orderId, "SUCCESSFUL")))
        assertEquals(0, conflicts(id))

        assertEquals(204, deliver(operator.callback(orderId, "FAILED", reason = "DECLINED")))
        assertEquals("succeeded", read(id).get("status").asString())
        assertEquals(1, conflicts(id))
    }

    @Test
    fun `a callback with a wrong signature has no effect`() {
        val payment = create(body()).json()
        val callback = operator.callback(payment.get("provider_order_id").asString(), "SUCCESSFUL")
        assertEquals(401, deliver(callback, signature = "sha256=00"))
        assertEquals("pending", read(payment.get("id").asString()).get("status").asString())
    }

    @Test
    fun `a success for another amount keeps the payment pending`() {
        val payment = create(body()).json()
        val id = payment.get("id").asString()
        deliver(operator.callback(payment.get("provider_order_id").asString(), "SUCCESSFUL", amount = "1249.00"))
        assertEquals("pending", read(id).get("status").asString())
        assertEquals(1, conflicts(id))
    }

    @Test
    fun `synchronizing a terminal payment does not call the operator`() {
        val payment = create(body()).json()
        val id = payment.get("id").asString()
        val orderId = payment.get("provider_order_id").asString()
        operator.statuses[orderId] = "FAILED"
        val synchronized = json.readTree(client.post().uri("/v1/payments/{id}/synchronize", id).retrieve().body(ByteArray::class.java))
        assertEquals("failed", synchronized.get("status").asString())
        assertEquals("unspecified", synchronized.get("failure_reason").asString())

        val calls = operator.calls.get()
        client.post().uri("/v1/payments/{id}/synchronize", id).retrieve().toBodilessEntity()
        assertEquals(calls, operator.calls.get())
    }

    @Test
    fun `an operator error is not recorded so the same key can be retried`() {
        val key = UUID.randomUUID().toString()
        val body = body()
        operator.failWith = 500
        val failed = create(body, key)
        assertEquals(502, failed.statusCode.value())
        assertEquals("unknown", failed.json().get("effect").asString())

        operator.failWith = null
        assertEquals(201, create(body, key).statusCode.value())
    }

    companion object {
        private val operator = FakeAlphaOperator()

        @JvmStatic
        @DynamicPropertySource
        fun operatorProperties(registry: DynamicPropertyRegistry) {
            registry.add("openfsp.providers.mock_alpha.base-url") { operator.baseUrl }
            registry.add("openfsp.provider-timeout") { "2s" }
        }

        @JvmStatic
        @AfterAll
        fun stop() = operator.close()
    }
}
