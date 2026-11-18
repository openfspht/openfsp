package org.openfsp.gateway

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.openfsp.gateway.SimulatorProcess.Companion.items
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.auth.api.ManageKeys
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.payment.api.ReconcilePendingPayments
import org.openfsp.protocol.OrderIdDerivation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("development", "test", "conformance")
class SimulatedProvidersEndToEndTest {
    @Autowired
    private lateinit var json: JsonMapper

    @Autowired
    private lateinit var keys: ManageKeys

    @Autowired
    private lateinit var orderIds: OrderIdDerivation

    @Autowired
    private lateinit var reconciliation: ReconcilePendingPayments

    @Autowired
    private lateinit var jdbc: JdbcClient

    @Autowired
    private lateinit var calendar: BusinessCalendar

    private val key by lazy { keys.issue("simulated-providers", setOf(Scope.PAYMENTS_READ, Scope.PAYMENTS_WRITE, Scope.CAPABILITIES_READ, Scope.STATEMENTS_READ)).secret }

    private val gateway by lazy {
        RestClient
            .builder()
            .requestFactory(JdkClientHttpRequestFactory())
            .baseUrl("http://127.0.0.1:$GATEWAY_PORT")
            .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
            .build()
    }

    private class Created(
        val reference: String,
        val orderId: String,
        val idempotencyKey: String,
        val response: ResponseEntity<ByteArray>,
        val body: JsonNode,
    ) {
        val status: Int get() = response.statusCode.value()
        val id: String get() = body.get("id").asString()
    }

    private fun create(
        provider: String,
        scenario: String? = null,
        reference: String = "INV-${UUID.randomUUID()}",
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): Created {
        val orderId = orderIds.derive(reference)
        scenario?.let { assertEquals(204, simulator.declare(provider, orderId, it)) }
        val response = post(provider, reference, idempotencyKey)
        return Created(reference, orderId, idempotencyKey, response, json.readTree(response.body))
    }

    private fun post(
        provider: String,
        reference: String,
        idempotencyKey: String,
    ): ResponseEntity<ByteArray> =
        gateway
            .post()
            .uri("/v1/payments")
            .header("Authorization", "Bearer $key")
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"reference":"$reference","amount":{"amount":125000,"currency":"HTG"},"provider":"$provider"}""")
            .retrieve()
            .toEntity(ByteArray::class.java)

    private fun get(path: String): JsonNode = json.readTree(gateway.get().uri(path).header("Authorization", "Bearer $key").retrieve().body(ByteArray::class.java))

    private fun read(id: String): JsonNode = get("/v1/payments/$id")

    private fun synchronize(id: String): JsonNode =
        json.readTree(
            gateway
                .post()
                .uri("/v1/payments/{id}/synchronize", id)
                .header("Authorization", "Bearer $key")
                .retrieve()
                .body(ByteArray::class.java),
        )

    private fun <T> eventually(
        within: Duration = Duration.ofSeconds(15),
        probe: () -> T?,
    ): T {
        val deadline = Instant.now().plus(within)
        while (Instant.now().isBefore(deadline)) {
            probe()?.let { return it }
            Thread.sleep(POLL_MILLIS)
        }
        return assertNotNull(probe(), "condition not reached within $within")
    }

    private fun awaitStatus(
        id: String,
        status: String,
        observe: (String) -> JsonNode = ::read,
    ): JsonNode = eventually { observe(id).takeIf { it.get("status").asString() == status } }

    private fun awaitSettled(
        operator: String,
        orderId: String,
        callbacks: Int = 0,
    ): JsonNode = eventually { simulator.payment(operator, orderId)?.takeIf { !it.get("outcome").isNull && it.get("callbacks_sent").asInt() >= callbacks } }

    private fun creations(
        operator: String,
        orderId: String,
    ): List<JsonNode> = simulator.journal(operator, orderId).filter { it.get("method").asString() == "POST" && it.get("path").asString() == "/$operator/v1/payments" }

    private fun webhooks(
        paymentId: String,
        type: String? = null,
    ): List<JsonNode> = receiver.received.map { json.readTree(it.body) }.filter { it.get("resource_id").asString() == paymentId && (type == null || it.get("type").asString() == type) }

    private fun callbackUrlSentTo(
        operator: String,
        orderId: String,
        member: String,
    ): URI = URI(json.readTree(simulator.journal(operator, orderId).first { it.get("method").asString() == "POST" }.get("body").asString()).get(member).asString())

    private fun postCallback(
        path: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): Int =
        gateway
            .post()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .headers { http -> headers.forEach(http::set) }
            .body(body)
            .retrieve()
            .toBodilessEntity()
            .statusCode
            .value()

    private fun forgedEvent(created: Created): String {
        val payment = created.body
        val upstream = payment.get("metadata").get("gateway.provider_reference").asString()
        val at = payment.get("created_at").asString()
        val data =
            """{"id":"$upstream","reference":"${created.orderId}","status":"succeeded","amount":{"amount":125000,"currency":"HTG"},"provider":"mock_openfsp",""" +
                """"created_at":"$at","updated_at":"$at","completed_at":"$at"}"""
        return """{"id":"evt_${UUID.randomUUID()}","type":"payment.succeeded","created_at":"$at","sequence":2,"resource_type":"payment","resource_id":"$upstream",""" +
            """"reference":"${created.orderId}","previous_status":"pending","data":$data}"""
    }

    @Test
    fun `each simulated provider is announced with what it performs and the statement the gateway keeps`() {
        val announced =
            get("/v1/capabilities").get("providers").items().associate { provider ->
                provider.get("id").asString() to provider.get("capabilities").items().map { it.asString() }.toSet()
            }
        val emit = "webhooks.emit"
        assertEquals(setOf("payments", "payments.fee", "payments.lookup", "payments.statement", emit), announced["mock_openfsp"])
        assertEquals(setOf("payments", "payments.lookup", "payments.statement", emit), announced["mock_moncash"])
        assertEquals(setOf("payments", "payments.lookup", "payments.statement", emit), announced["mock_natcash"])
        assertEquals(setOf("payments", "payments.statement", emit), announced["mock_no_source"])
        assertEquals(setOf("payments", "payments.statement", emit), announced["mock_statement_only"])
        assertTrue(announced.values.flatten().none { it == "webhooks.verify" || it == "webhooks.per_payment_url" })
        val discovery = json.readTree(gateway.get().uri("/.well-known/openfsp-configuration").retrieve().body(ByteArray::class.java))
        assertEquals(listOf("payments", "payments.fee", "payments.lookup", "payments.statement", emit), discovery.get("capabilities_supported").items().map { it.asString() })
    }

    @Test
    fun `the upstream OpenFSP server describes itself alone and publishes only public Ed25519 webhook keys`() {
        val upstreamKey = "ofsp_test_MockOpenFspKeyPublishedForTheSimulator"
        val providers =
            json.readTree(simulator.client.get().uri("/mock_openfsp/v1/capabilities").header("Authorization", "Bearer $upstreamKey").retrieve().body(ByteArray::class.java)).get("providers").items()
        assertEquals(listOf("mock_openfsp"), providers.map { it.get("id").asString() })
        assertEquals("mock_openfsp", create("mock_openfsp", "SUCCESS").body.get("provider").asString())

        val published = simulator.read("/mock_openfsp/.well-known/openfsp-configuration").get("webhook_keys").items()
        assertTrue(published.isNotEmpty())
        published.forEach { jwk ->
            assertEquals("OKP", jwk.get("kty").asString())
            assertEquals("Ed25519", jwk.get("crv").asString())
            assertEquals("sig", jwk.get("use").asString())
            assertTrue(jwk.get("kid").asString().isNotEmpty() && jwk.get("x").asString().isNotEmpty())
            assertTrue(jwk.get("openfsp_status").asString() in setOf("active", "retired"))
            assertTrue(jwk.get("d") == null)
        }
        val keysUnderApi = simulator.client.get().uri("/mock_openfsp/v1/webhooks/keys").header("Authorization", "Bearer $upstreamKey").retrieve().toBodilessEntity()
        assertEquals(404, keysUnderApi.statusCode.value())
    }

    @Test
    fun `a creation answered too late gives provider-timeout and a lookup precedes any second creation`() {
        val created = create("mock_openfsp", "TIMEOUT_THEN_NOTHING")
        assertEquals(504, created.status)
        assertEquals("https://openfsp.org/problems/provider-timeout", created.body.get("type").asString())
        assertTrue(created.body.get("retryable").asBoolean())
        assertEquals("unknown", created.body.get("effect").asString())
        val first = eventually { creations("mock_openfsp", created.orderId).singleOrNull() }

        val resent = json.readTree(post("mock_openfsp", created.reference, created.idempotencyKey).body)
        assertEquals("https://openfsp.org/problems/provider-timeout", resent.get("type").asString())
        assertEquals("unknown", resent.get("effect").asString())
        val since = eventually { simulator.journal("mock_openfsp", created.orderId).filter { it.get("received_at").asString() > first.get("received_at").asString() }.takeIf { entries -> entries.any { it.get("method").asString() == "POST" } } }
        assertEquals("GET", since.first().get("method").asString())
        assertTrue(since.first().get("path").asString().startsWith("/mock_openfsp/v1/payments?reference="))
        assertEquals(first.get("body").asString(), since.first { it.get("method").asString() == "POST" }.get("body").asString())

        val found = get("/v1/payments?reference=${created.reference}").get("data").items()
        assertTrue(found.isNotEmpty() && found.all { it.get("status").asString() == "pending" })
    }

    @Test
    fun `a creation resent after an unknown outcome repeats the first request byte for byte and is created upstream`() {
        val created = create("mock_openfsp", "TIMEOUT_THEN_NOTHING")
        assertEquals(504, created.status)
        eventually { creations("mock_openfsp", created.orderId).singleOrNull() }
        assertEquals(204, simulator.declare("mock_openfsp", created.orderId, "SUCCESS"))

        val resent = post("mock_openfsp", created.reference, created.idempotencyKey)
        assertEquals(201, resent.statusCode.value())
        val (first, second) = creations("mock_openfsp", created.orderId)
        assertEquals(first.get("body").asString(), second.get("body").asString())
        assertEquals(first.get("headers").get("idempotency-key").asString(), second.get("headers").get("idempotency-key").asString())
        assertEquals(201, second.get("status").asInt())
        val upstream = json.readTree(second.get("response_body").asString()).get("id").asString()
        val payment = json.readTree(resent.body)
        assertEquals(upstream, payment.get("metadata").get("gateway.provider_reference").asString())
        assertEquals(1, simulator.payments("mock_openfsp", created.orderId).size)
        awaitStatus(payment.get("id").asString(), "succeeded", ::synchronize)
    }

    @Test
    fun `a creation that succeeded behind a timeout is created once at the provider and succeeds by synchronization`() {
        val created = create("mock_openfsp", "TIMEOUT_THEN_SUCCESS")
        assertEquals(504, created.status)
        assertEquals("https://openfsp.org/problems/provider-timeout", created.body.get("type").asString())
        assertEquals("unknown", created.body.get("effect").asString())
        post("mock_openfsp", created.reference, created.idempotencyKey)

        val id = get("/v1/payments?reference=${created.reference}").get("data").get(0).get("id").asString()
        eventually { creations("mock_openfsp", created.orderId).takeIf { it.isNotEmpty() } }
        assertEquals(1, simulator.payments("mock_openfsp", created.orderId).size)
        awaitStatus(id, "succeeded", ::synchronize)
    }

    @Test
    fun `an unknown creation outcome at a provider without idempotency or lookup is never resent`() {
        val created = create("mock_no_source", "TIMEOUT")
        assertEquals(504, created.status)
        assertEquals("https://openfsp.org/problems/provider-timeout", created.body.get("type").asString())
        assertEquals("unknown", created.body.get("effect").asString())
        eventually { creations("mock_no_source", created.orderId).singleOrNull() }

        jdbc.sql("update payments set updated_at = now() - interval '1 minute' where reference = ?").param(created.reference).update()
        reconciliation.reconcile(RECONCILIATION_BATCH)
        Thread.sleep(NO_RESEND_OBSERVATION_MILLIS)
        assertEquals(1, creations("mock_no_source", created.orderId).size)
        assertEquals("pending", get("/v1/payments?reference=${created.reference}").get("data").get(0).get("status").asString())
    }

    @Test
    fun `the order identifier is published in metadata, sent to the provider once and selects its scenario`() {
        val created = create("mock_openfsp", "DECLINE")
        assertEquals(201, created.status)
        assertEquals(created.orderId, created.body.get("metadata").get("gateway.order_id").asString())
        assertEquals(1, creations("mock_openfsp", created.orderId).size)
        assertEquals("declined", awaitStatus(created.id, "failed").get("failure_reason").asString())
    }

    @Test
    fun `a provider that refuses the connection gives provider-unavailable without effect`() {
        val created = create("mock_unreachable")
        assertEquals(503, created.status)
        assertEquals(MediaType.parseMediaType("application/problem+json"), created.response.headers.contentType)
        assertEquals("https://openfsp.org/problems/provider-unavailable", created.body.get("type").asString())
        assertTrue(created.body.get("retryable").asBoolean())
        assertEquals("none", created.body.get("effect").asString())
    }

    @Test
    fun `an upstream problem is relayed in detail as its type segment and detail, never as type or effect`() {
        val created = create("mock_openfsp", "PROVIDER_ERROR")
        assertEquals(502, created.status)
        assertEquals("https://openfsp.org/problems/provider-error", created.body.get("type").asString())
        assertEquals("unknown", created.body.get("effect").asString())
        assertTrue(created.body.get("provider_detail") == null)
        val upstream = json.readTree(creations("mock_openfsp", created.orderId).last().get("response_body").asString())
        val message = upstream.get("detail")?.asString() ?: upstream.get("title").asString()
        assertEquals("${upstream.get("type").asString().substringAfterLast('/')}: $message", created.body.get("detail").asString())
    }

    @Test
    fun `credentials the provider echoes in an error are redacted from the problem detail`() {
        val created = create("mock_openfsp", "CREDENTIAL_ECHO")
        assertEquals(502, created.status)
        assertEquals(MediaType.parseMediaType("application/problem+json"), created.response.headers.contentType)
        val detail = created.body.get("detail").asString()
        assertTrue(detail.contains("[REDACTED]"), detail)
        assertTrue(!created.response.body!!.decodeToString().contains("ofsp_test_MockOpenFspKeyPublishedForTheSimulator"))
    }

    @Test
    fun `a success for another amount leaves the payment pending`() {
        val created = create("mock_openfsp", "AMOUNT_MISMATCH")
        assertEquals(201, created.status)
        awaitSettled("mock_openfsp", created.orderId, callbacks = 1)
        assertEquals("pending", synchronize(created.id).get("status").asString())
        val payment = read(created.id)
        assertEquals("pending", payment.get("status").asString())
        assertEquals(125000, payment.get("amount").get("amount").asInt())
    }

    @Test
    fun `the same signed callback received several times gives one transition and one event`() {
        val created = create("mock_openfsp", "DUPLICATE_CALLBACK")
        awaitSettled("mock_openfsp", created.orderId, callbacks = 1)
        val completedAt = awaitStatus(created.id, "succeeded").get("completed_at").asString()
        eventually { webhooks(created.id, "payment.succeeded").takeIf { it.isNotEmpty() } }
        Thread.sleep(QUIET_MILLIS)
        assertEquals(1, creations("mock_openfsp", created.orderId).size)
        assertEquals(completedAt, read(created.id).get("completed_at").asString())
        assertEquals(1, webhooks(created.id, "payment.succeeded").size)
    }

    @Test
    fun `a late callback on a payment made terminal by synchronization changes nothing and emits nothing`() {
        val created = create("mock_openfsp", "LATE_CALLBACK")
        val settled = awaitSettled("mock_openfsp", created.orderId)
        val completedAt = awaitStatus(created.id, "succeeded", ::synchronize).get("completed_at").asString()
        eventually { webhooks(created.id, "payment.succeeded").takeIf { it.isNotEmpty() } }

        assertEquals(200, simulator.deliverCallback(settled.get("payment_id").asString()))
        Thread.sleep(QUIET_MILLIS)
        val payment = read(created.id)
        assertEquals("succeeded", payment.get("status").asString())
        assertEquals(completedAt, payment.get("completed_at").asString())
        assertEquals(listOf("payment.created", "payment.succeeded"), webhooks(created.id).map { it.get("type").asString() })
    }

    @Test
    fun `a success then a failure from the provider leaves the payment succeeded and never emits payment failed`() {
        val created = create("mock_openfsp", "CONTRADICT")
        val completedAt = awaitStatus(created.id, "succeeded", ::synchronize).get("completed_at").asString()
        val again = synchronize(created.id)
        assertEquals("succeeded", again.get("status").asString())
        assertEquals(completedAt, again.get("completed_at").asString())
        assertEquals(completedAt, read(created.id).get("completed_at").asString())
        eventually { webhooks(created.id, "payment.succeeded").takeIf { it.isNotEmpty() } }
        Thread.sleep(QUIET_MILLIS)
        assertTrue(webhooks(created.id, "payment.failed").isEmpty())
    }

    @Test
    fun `a status the gateway has never seen leaves the payment pending`() {
        val created = create("mock_openfsp", "UNKNOWN_STATUS")
        awaitSettled("mock_openfsp", created.orderId)
        assertEquals("pending", synchronize(created.id).get("status").asString())
        assertEquals("pending", read(created.id).get("status").asString())
    }

    @Test
    fun `an unsigned success callback outside the per-payment URL causes no transition`() {
        val created = create("mock_openfsp", "PENDING_FOREVER")
        assertEquals("pending", created.body.get("status").asString())
        assertEquals(401, postCallback("/callbacks/mock_openfsp", forgedEvent(created)))
        assertEquals("pending", read(created.id).get("status").asString())
    }

    @Test
    fun `a success callback on the per-payment URL whose signature does not verify causes no transition`() {
        val created = create("mock_openfsp", "PENDING_FOREVER")
        val callbackUrl = callbackUrlSentTo("mock_openfsp", created.orderId, "callback_url")
        val forged =
            mapOf(
                "Content-Digest" to "sha-256=:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=:",
                "OpenFSP-Event-Id" to "evt_${UUID.randomUUID()}",
                "OpenFSP-Event-Type" to "payment.succeeded",
                "Signature-Input" to
                    """sig1=("@method" "@target-uri" "content-type" "content-digest" "openfsp-event-id" "openfsp-event-type");created=${Instant.now().epochSecond};""" +
                    """expires=${Instant.now().epochSecond + 300};keyid="whk_forged";nonce="${UUID.randomUUID()}";alg="ed25519";tag="openfsp-webhook"""",
                "Signature" to "sig1=:bT8xVq2Lw0nR5cJ3kYpA7fHdbT8xVq2Lw0nR5cJ3kYpA7fHdbT8xVq2Lw0nR5cJ3kYpA7fHdbT8xVq2Lw0nR5cJ3kQ==:",
            )
        assertEquals(404, postCallback(callbackUrl.path, forgedEvent(created), forged))
        assertEquals("pending", read(created.id).get("status").asString())
        assertEquals("pending", synchronize(created.id).get("status").asString())
    }

    @Test
    fun `a NatCash callback on the right per-payment URL with a forged signature causes no transition`() {
        val created = create("mock_natcash", "PENDING_FOREVER")
        assertEquals("pending", created.body.get("status").asString())
        val callbackUrl = callbackUrlSentTo("mock_natcash", created.orderId, "callbackUrl")
        assertEquals(404, postCallback(callbackUrl.path, """{"orderNumber":"${created.orderId}","code":"1","transId":"MN-forged","signature":"forged"}"""))
        assertEquals("pending", read(created.id).get("status").asString())
        assertEquals("pending", synchronize(created.id).get("status").asString())
    }

    @Test
    fun `a NatCash callback on a per-payment URL with a wrong token is rejected without effect`() {
        val created = create("mock_natcash", "PENDING_FOREVER")
        val callbackUrl = callbackUrlSentTo("mock_natcash", created.orderId, "callbackUrl")
        assertEquals(404, postCallback("${callbackUrl.path}0", """{"orderNumber":"${created.orderId}","code":"1","transId":"MN-forged","signature":"forged"}"""))
        assertEquals("pending", read(created.id).get("status").asString())
        assertEquals("pending", synchronize(created.id).get("status").asString())
    }

    @Test
    fun `two NatCash payments receive distinct callback URLs ending with a token of at least 128 bits`() {
        val first = create("mock_natcash", "PENDING_FOREVER")
        val second = create("mock_natcash", "PENDING_FOREVER")
        val firstUrl = callbackUrlSentTo("mock_natcash", first.orderId, "callbackUrl")
        val secondUrl = callbackUrlSentTo("mock_natcash", second.orderId, "callbackUrl")
        val token = Regex("/([A-Za-z0-9_-]{22,}|[0-9a-fA-F]{32,})$")
        assertTrue(token.containsMatchIn(firstUrl.toString()), firstUrl.toString())
        assertTrue(token.containsMatchIn(secondUrl.toString()), secondUrl.toString())
        assertTrue(firstUrl != secondUrl)
        assertEquals(1, simulator.journal("mock_natcash", firstUrl.toString()).count { it.get("method").asString() == "POST" })
    }

    @Test
    fun `a NatCash callback on the per-payment URL settles the payment without a synchronization`() {
        val created = create("mock_natcash", "SUCCESS")
        assertEquals("pending", created.body.get("status").asString())
        awaitSettled("mock_natcash", created.orderId, callbacks = 1)
        assertTrue(!awaitStatus(created.id, "succeeded").get("completed_at").isNull)
    }

    @Test
    fun `the statement of a provider lists its succeeded payments from the gateway alone`() {
        val created = create("mock_natcash", "SUCCESS")
        awaitSettled("mock_natcash", created.orderId, callbacks = 1)
        val payment = awaitStatus(created.id, "succeeded")
        val day = calendar.businessDate(Instant.parse(payment.get("completed_at").asString()))

        val entry = get("/v1/statements?provider=mock_natcash&business_date=$day").get("data").items().single { it.get("payment").asString() == created.id }

        assertEquals(payment.get("reference").asString(), entry.get("reference").asString())
        assertEquals(payment.get("completed_at").asString(), entry.get("booked_at").asString())
        assertEquals(payment.get("amount"), entry.get("amount"))
        assertTrue(!payment.has("fee") && !entry.has("fee"))
        assertTrue(simulator.journal("mock_openfsp", "/v1/statements").isEmpty())
    }

    @Test
    fun `no automatic source settles a payment at a provider without lookup or callbacks`() {
        val created = create("mock_statement_only", "NO_CALLBACK")
        assertEquals(201, created.status)
        awaitSettled("mock_statement_only", created.orderId)
        reconciliation.reconcile(RECONCILIATION_BATCH)
        assertEquals("pending", read(created.id).get("status").asString())
        assertTrue(simulator.journal("mock_statement_only", "/v1/statements").isEmpty())
    }

    companion object {
        private val GATEWAY_PORT = SimulatorProcess.freePort()
        private val CLOSED_PORT = SimulatorProcess.freePort()
        private const val POLL_MILLIS = 100L
        private const val QUIET_MILLIS = 1_000L
        private const val NO_RESEND_OBSERVATION_MILLIS = 3_000L
        private const val RECONCILIATION_BATCH = 500
        private val receiver = WebhookReceiver()
        private val simulatorProcess =
            SimulatorProcess(
                "simulator-simulated-providers",
                mapOf(
                    "spring.profiles.active" to "conformance",
                    "simulator.settle-after" to "300ms",
                    "simulator.hang-duration" to "2500ms",
                    "simulator.late-callback-delay" to "1h",
                ),
            )
        private val simulator = simulatorProcess

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            val base = simulatorProcess.baseUrl
            registry.add("server.port") { GATEWAY_PORT }
            registry.add("openfsp.public-base-url") { "http://127.0.0.1:$GATEWAY_PORT" }
            registry.add("openfsp.provider-timeout") { "1s" }
            registry.add("openfsp.reconciliation-interval") { "1h" }
            registry.add("openfsp.webhooks.poll-interval") { "100ms" }
            registry.add("openfsp.webhooks.endpoints.demo-shop.url") { receiver.url }
            registry.add("openfsp.providers.mock_openfsp.base-url") { "$base/mock_openfsp" }
            registry.add("openfsp.providers.mock_moncash.base-url") { "$base/mock_moncash/Api" }
            registry.add("openfsp.providers.mock_moncash.secondary-url") { "$base/mock_moncash/Moncash-middleware" }
            registry.add("openfsp.providers.mock_natcash.base-url") { "$base/mock_natcash/api/online-payment" }
            registry.add("openfsp.providers.mock_no_source.base-url") { "$base/mock_no_source" }
            registry.add("openfsp.providers.mock_statement_only.base-url") { "$base/mock_statement_only" }
            registry.add("openfsp.providers.mock_unreachable.base-url") { "http://127.0.0.1:$CLOSED_PORT/mock_unreachable" }
            registry.add("openfsp.providers.mock_alpha.base-url") { "http://127.0.0.1:$CLOSED_PORT/mock_alpha" }
            registry.add("openfsp.providers.mock_epsilon.base-url") { "http://127.0.0.1:$CLOSED_PORT/mock_epsilon" }
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            simulatorProcess.close()
            receiver.close()
        }
    }
}
