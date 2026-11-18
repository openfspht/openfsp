package org.openfsp.gateway.conformance

import org.junit.jupiter.api.AfterAll
import org.openfsp.gateway.FakeOpenFspOperator
import org.openfsp.gateway.FakeProfile
import org.openfsp.gateway.TestcontainersConfiguration
import org.openfsp.gateway.WebhookReceiver
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.auth.api.ManageKeys
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MovableClock : Clock() {
    @Volatile
    var ahead: Duration = Duration.ZERO

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = Instant.now().plus(ahead)
}

@TestConfiguration(proxyBeanMethods = false)
class MovableClockConfiguration {
    @Bean
    @Primary
    fun movableClock() = MovableClock()
}

@Import(TestcontainersConfiguration::class, MovableClockConfiguration::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["openfsp.conformance.enabled=true", "openfsp.conformance.settle-after=300ms", "openfsp.webhooks.poll-interval=100ms", "openfsp.reconciliation-interval=1h"],
)
@ActiveProfiles("development", "test")
class ConformanceControlTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var json: JsonMapper

    @Autowired
    private lateinit var keys: ManageKeys

    @Autowired
    private lateinit var clock: MovableClock

    private val client by lazy {
        RestClient
            .builder()
            .requestFactory(JdkClientHttpRequestFactory())
            .baseUrl("http://127.0.0.1:$port")
            .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
            .build()
    }

    private class Preparation(
        val apiKey: String,
        val restrictedKey: String,
        val receiver: WebhookReceiver,
    )

    private fun prepare(): Preparation {
        val receiver = WebhookReceiver().also(receivers::add)
        val prepared = client.post().uri("/conformance").contentType(MediaType.APPLICATION_JSON).body(mapOf("webhook_url" to receiver.url)).retrieve().toEntity(ByteArray::class.java)
        assertEquals(201, prepared.statusCode.value())
        val fixtures = json.readTree(prepared.body)
        assertEquals("http://127.0.0.1:8080$CONTROL", fixtures.get("control_endpoint").asString())
        return Preparation(fixtures.get("api_key").asString(), fixtures.get("inputs").get("restricted-key").asString(), receiver)
    }

    private fun send(
        method: String,
        path: String,
        key: String?,
        body: String? = null,
        idempotencyKey: String? = null,
    ): ResponseEntity<ByteArray> {
        val request = client.method(HttpMethod.valueOf(method)).uri(path)
        key?.let { request.header("Authorization", "Bearer $it") }
        idempotencyKey?.let { request.header("Idempotency-Key", it) }
        body?.let { request.contentType(MediaType.APPLICATION_JSON).body(it) }
        return request.retrieve().toEntity(ByteArray::class.java)
    }

    private fun ResponseEntity<ByteArray>.tree(): JsonNode = json.readTree(body)

    private fun JsonNode.items(): List<JsonNode> = (0 until size()).map { get(it) }

    private fun ResponseEntity<ByteArray>.problem(): String = tree().get("type").asString().substringAfterLast('/')

    private fun declare(
        key: String,
        reference: String,
        scenario: String,
    ) = send("PUT", "$CONTROL/references/$reference/scenario", key, """{"scenario":"$scenario"}""")

    private fun state(
        key: String?,
        reference: String,
    ) = send("GET", "$CONTROL/references/$reference", key)

    private fun create(
        key: String,
        reference: String,
        idempotencyKey: String = UUID.randomUUID().toString(),
        provider: String = "mock_alpha",
    ) = send("POST", "/v1/payments", key, """{"reference":"$reference","amount":{"amount":125000,"currency":"HTG"},"provider":"$provider"}""", idempotencyKey)

    private fun read(
        key: String,
        id: String,
    ): JsonNode = send("GET", "/v1/payments/$id", key).tree()

    private fun synchronize(
        key: String,
        id: String,
    ) = send("POST", "/v1/payments/$id/synchronize", key)

    private fun await(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            Thread.sleep(100)
        }
        assertTrue(condition())
    }

    private fun Preparation.events(id: String): List<JsonNode> =
        receiver.received.map { json.readTree(it.body) }.filter { it.get("resource_id").asString() == id }.sortedBy { it.get("sequence").asInt() }

    private fun reference() = "INV-${UUID.randomUUID()}"

    @Test
    fun `the control interface answers only the api key of a preparation`() {
        val run = prepare()
        val reference = reference()
        val missing = state(null, reference)
        assertEquals(401 to "unauthenticated", missing.statusCode.value() to missing.problem())
        assertEquals("Bearer", missing.headers.getFirst("WWW-Authenticate"))
        assertEquals(401, state("ofsp_test_${"x".repeat(40)}", reference).statusCode.value())

        val outsider = keys.issue("control-outsider", Scope.entries.toSet()).secret
        val refused = declare(outsider, reference, "DECLINE")
        assertEquals(403 to "forbidden", refused.statusCode.value() to refused.problem())
        assertEquals(403, state(run.restrictedKey, reference).statusCode.value())

        val unknown = declare(run.apiKey, reference, "CONTRADICT")
        assertEquals(422 to "invalid-field", unknown.statusCode.value() to unknown.problem())
        assertEquals(204, declare(run.apiKey, reference, "DECLINE").statusCode.value())
        val absent = state(run.apiKey, reference)
        assertEquals(404 to "not-found", absent.statusCode.value() to absent.problem())
    }

    @Test
    fun `a preparation neither sees nor resolves the payments of another`() {
        val first = prepare()
        val second = prepare()
        val reference = reference()
        assertEquals(204, declare(first.apiKey, reference, "PENDING_FOREVER").statusCode.value())
        val id = create(first.apiKey, reference).tree().get("id").asString()
        assertEquals(id, state(first.apiKey, reference).tree().get("payment").asString())

        assertEquals(404, state(second.apiKey, reference).statusCode.value())
        assertEquals(404, send("POST", "$CONTROL/payments/$id/resolve", second.apiKey, """{"outcome":"succeeded"}""").statusCode.value())
        assertEquals(404, send("POST", "$CONTROL/payments/$id/callback", second.apiKey).statusCode.value())
        assertFalse(state(first.apiKey, reference).tree().get("settled").asBoolean())

        val shared = reference()
        assertEquals(204, declare(second.apiKey, shared, "DECLINE").statusCode.value())
        val calls = operator.calls.get()
        val unscripted = create(first.apiKey, shared).tree()
        assertEquals(calls + 1, operator.calls.get())
        assertEquals("MA-${unscripted.get("metadata").get("gateway.order_id").asString()}", unscripted.get("metadata").get("gateway.provider_reference").asString())
        val conflict = send("POST", "$CONTROL/payments/${unscripted.get("id").asString()}/resolve", first.apiKey, """{"outcome":"succeeded"}""")
        assertEquals(409 to "state-conflict", conflict.statusCode.value() to conflict.problem())
    }

    @Test
    fun `a declined payment fails through the notification path without reaching the operator`() {
        val run = prepare()
        val reference = reference()
        val calls = operator.calls.get()
        declare(run.apiKey, reference, "DECLINE")
        val created = create(run.apiKey, reference)
        assertEquals(201, created.statusCode.value())
        assertEquals("pending", created.tree().get("status").asString())
        assertEquals("payer_approval", created.tree().get("next_action").get("type").asString())
        val id = created.tree().get("id").asString()

        await { state(run.apiKey, reference).tree().let { it.get("settled").asBoolean() && it.get("callbacks_sent").asInt() == 1 } }
        val failed = read(run.apiKey, id)
        assertEquals("failed" to "declined", failed.get("status").asString() to failed.get("failure_reason").asString())
        val detail = send("GET", "/admin/payments/$id", null).tree()
        assertEquals(listOf("creation", "signed_callback"), detail.get("transitions").items().map { it.get("source").asString() })

        await { run.events(id).size == 2 }
        val (announced, event) = run.events(id)
        assertEquals("payment.created", announced.get("type").asString())
        assertEquals("payment.failed" to "pending", event.get("type").asString() to event.get("previous_status").asString())
        assertEquals("declined", event.get("data").get("failure_reason").asString())
        assertEquals(calls, operator.calls.get())
    }

    @Test
    fun `an expiring payment is pending before its minute and expired by synchronization after it`() {
        val run = prepare()
        val reference = reference()
        declare(run.apiKey, reference, "EXPIRE")
        val created = create(run.apiKey, reference).tree()
        val id = created.get("id").asString()
        assertEquals(Duration.ofMinutes(1), Duration.between(Instant.parse(created.get("created_at").asString()), Instant.parse(created.get("expires_at").asString())))
        assertEquals("pending", synchronize(run.apiKey, id).tree().get("status").asString())

        clock.ahead = clock.ahead.plusSeconds(61)
        val expired = synchronize(run.apiKey, id).tree()
        assertEquals("expired", expired.get("status").asString())
        assertTrue(expired.has("completed_at"))
        assertFalse(expired.has("failure_reason"))
        await { run.events(id).any { it.get("type").asString() == "payment.expired" && it.get("previous_status").asString() == "pending" } }
        assertTrue(state(run.apiKey, reference).tree().get("settled").asBoolean())
    }

    @Test
    fun `a pending payment stays pending until the control interface resolves it`() {
        val run = prepare()
        val reference = reference()
        val calls = operator.calls.get()
        declare(run.apiKey, reference, "PENDING_FOREVER")
        val id = create(run.apiKey, reference).tree().get("id").asString()
        Thread.sleep(600)
        assertEquals("pending", synchronize(run.apiKey, id).tree().get("status").asString())
        assertFalse(state(run.apiKey, reference).tree().get("settled").asBoolean())

        val invalid = send("POST", "$CONTROL/payments/$id/resolve", run.apiKey, """{"outcome":"canceled"}""")
        assertEquals(422 to "invalid-field", invalid.statusCode.value() to invalid.problem())
        assertEquals(204, send("POST", "$CONTROL/payments/$id/resolve", run.apiKey, """{"outcome":"succeeded"}""").statusCode.value())
        await { read(run.apiKey, id).get("status").asString() == "succeeded" }
        await { run.events(id).size == 2 }
        assertEquals(listOf("payment.created", "payment.succeeded"), run.events(id).map { it.get("type").asString() })

        assertEquals(204, send("POST", "$CONTROL/payments/$id/callback", run.apiKey).statusCode.value())
        assertEquals(2, state(run.apiKey, reference).tree().get("callbacks_sent").asInt())
        Thread.sleep(400)
        assertEquals(2, run.events(id).size)
        assertEquals(calls, operator.calls.get())
    }

    @Test
    fun `resolve settles a payment whose provider has no lookup nor notification, with its event`() {
        val run = prepare()
        val reference = reference()
        val calls = operator.calls.get()
        declare(run.apiKey, reference, "PENDING_FOREVER")
        val id = create(run.apiKey, reference, provider = "mock_epsilon").tree().get("id").asString()
        val refused = synchronize(run.apiKey, id)
        assertEquals(422 to "capability-not-supported", refused.statusCode.value() to refused.problem())

        assertEquals(204, send("POST", "$CONTROL/payments/$id/resolve", run.apiKey, """{"outcome":"succeeded"}""").statusCode.value())
        val succeeded = read(run.apiKey, id)
        assertEquals("succeeded", succeeded.get("status").asString())
        assertEquals(succeeded, synchronize(run.apiKey, id).tree())
        val detail = send("GET", "/admin/payments/$id", null).tree()
        assertEquals(listOf("creation", "attestation"), detail.get("transitions").items().map { it.get("source").asString() })
        await { run.events(id).size == 2 }
        assertEquals("payment.succeeded" to "pending", run.events(id).last().let { it.get("type").asString() to it.get("previous_status").asString() })
        assertEquals(calls, operator.calls.get())
    }

    @Test
    fun `a provider error at creation answers provider-error and is not replayed`() {
        val run = prepare()
        val reference = reference()
        val idempotencyKey = UUID.randomUUID().toString()
        val calls = operator.calls.get()
        declare(run.apiKey, reference, "PROVIDER_ERROR")
        repeat(2) {
            val refused = create(run.apiKey, reference, idempotencyKey)
            assertEquals(502 to "provider-error", refused.statusCode.value() to refused.problem())
            assertEquals("unknown", refused.tree().get("effect").asString())
            assertTrue(refused.tree().get("retryable").asBoolean())
            assertFalse(refused.headers.containsHeader("Idempotent-Replay"))
        }
        val held = state(run.apiKey, reference).tree()
        assertFalse(held.get("settled").asBoolean())
        assertEquals("pending", read(run.apiKey, held.get("payment").asString()).get("status").asString())
        assertEquals(calls, operator.calls.get())
    }

    companion object {
        private const val CONTROL = "/conformance/control"
        private val receivers = mutableListOf<WebhookReceiver>()
        private val operator = FakeOpenFspOperator(
                mapOf(
                    "mock_alpha" to FakeProfile(listOf("payments", "payments.lookup", "webhooks.emit"), "payer_approval"),
                    "mock_epsilon" to FakeProfile(listOf("payments"), "payer_approval"),
                ),
            )

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("openfsp.providers.mock_alpha.base-url") { operator.url("mock_alpha") }
            registry.add("openfsp.providers.mock_epsilon.base-url") { operator.url("mock_epsilon") }
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            operator.close()
            receivers.forEach(WebhookReceiver::close)
        }
    }
}
