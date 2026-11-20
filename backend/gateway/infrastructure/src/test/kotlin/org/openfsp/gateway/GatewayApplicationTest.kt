package org.openfsp.gateway

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openfsp.gateway.account.MerchantAccount
import org.openfsp.gateway.account.api.ManageAccounts
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.auth.api.ManageKeys
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.payment.api.ReconcilePendingPayments
import org.openfsp.gateway.webhook.api.RotateSigningKeys
import org.openfsp.gateway.webhooks.WebhookNoncePurgeJob
import org.openfsp.protocol.ResourceIds
import org.openfsp.protocol.webhook.InMemoryNonceStore
import org.openfsp.protocol.webhook.NonceStore
import org.openfsp.protocol.webhook.Verification
import org.openfsp.protocol.webhook.WebhookVerifier
import org.openfsp.server.OrderIdDerivation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
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
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("development", "simulated", "test")
class GatewayApplicationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var json: JsonMapper

    @Autowired
    private lateinit var jdbc: JdbcClient

    @Autowired
    private lateinit var keys: ManageKeys

    @Autowired
    private lateinit var accounts: ManageAccounts

    @Autowired
    private lateinit var payments: ReconcilePendingPayments

    @Autowired
    private lateinit var calendar: BusinessCalendar

    @Autowired
    private lateinit var rotation: RotateSigningKeys

    @Autowired
    private lateinit var nonces: NonceStore

    @Autowired
    private lateinit var noncePurge: WebhookNoncePurgeJob

    @Autowired
    private lateinit var orderIds: OrderIdDerivation

    private val key by lazy { keys.issue(MerchantAccount.DEFAULT, "tests", setOf(Scope.PAYMENTS_READ, Scope.PAYMENTS_WRITE)).secret }

    private val anonymous by lazy {
        RestClient
            .builder()
            .requestFactory(JdkClientHttpRequestFactory())
            .baseUrl("http://127.0.0.1:$port")
            .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
            .build()
    }

    private val client by lazy { anonymous.mutate().defaultHeader("Authorization", "Bearer $key").build() }

    private fun readAs(
        authorization: String?,
        id: String = "pay_unknown",
    ) = anonymous
        .get()
        .uri("/v1/payments/{id}", id)
        .headers { if (authorization != null) it.set("Authorization", authorization) }
        .retrieve()
        .toEntity(ByteArray::class.java)

    @BeforeEach
    fun resetOperator() {
        operator.reset()
        receiver.respondWith = 204
    }

    private fun verifier(): WebhookVerifier {
        val jwks = json.readTree(anonymous.get().uri("/.well-known/openfsp-configuration").retrieve().body(ByteArray::class.java)).get("webhook_keys")
        val keys =
            jwks.associate { jwk ->
                val raw = Base64.getUrlDecoder().decode(jwk.get("x").asString())
                val spki = ED25519_SPKI_PREFIX + raw
                jwk.get("kid").asString() to KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(spki))
            }
        return WebhookVerifier(keys = { keys[it] }, nonces = InMemoryNonceStore())
    }

    private fun webhooksFor(id: String) = receiver.received.filter { json.readTree(it.body).get("resource_id").asString() == id }

    private fun recordedAttempts(paymentId: String) =
        jdbc
            .sql("select count(*) from webhook_attempts a join webhook_deliveries d on d.id = a.delivery_id join events e on e.id = d.event_id where e.payment_id = ?")
            .param(paymentId)
            .query(Int::class.java)
            .single()

    private fun await(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue(condition())
    }

    private fun body(
        reference: String = "INV-${UUID.randomUUID()}",
        amount: Long = 125000,
        provider: String = "mock_alpha",
    ) = """{"reference":"$reference","amount":{"amount":$amount,"currency":"HTG"},"provider":"$provider","payer":{"phone_number":"+50934567890"},"return_url":"https://shop.example/orders/$reference"}"""

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

    private fun JsonNode.items(): List<JsonNode> = (0 until size()).map { get(it) }

    private fun ResponseEntity<ByteArray>.json(): JsonNode = json.readTree(body)

    private fun ResponseEntity<ByteArray>.problem(): String = json().get("type").asString().substringAfterLast('/')

    private fun read(id: String): JsonNode = json.readTree(client.get().uri("/v1/payments/{id}", id).retrieve().body(ByteArray::class.java))

    private fun deliver(
        callback: SignedCallback,
        signature: String? = null,
    ) = anonymous
        .post()
        .uri("/callbacks/mock_alpha")
        .headers { headers ->
            callback.headers.forEach(headers::set)
            if (signature != null) headers.set("Signature", signature)
        }.body(callback.body.toByteArray())
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
        assertEquals(first.headers.location, replay.headers.location)
        assertNotEquals(first.headers.getFirst("Request-Id"), replay.headers.getFirst("Request-Id"))
        assertEquals(calls, operator.calls.get())
    }

    @Test
    fun `a request still in progress is refused until its lease expires, then taken over and answered with the payment it created`() {
        val key = UUID.randomUUID().toString()
        val reference = "INV-${UUID.randomUUID()}"
        val body = body(reference)
        val created = create(body, key).json()
        val calls = operator.calls.get()
        val interrupted = { claimedAt: String ->
            jdbc
                .sql("update idempotency_records set response_status = null, response_body = null, claimed_at = now() - cast(? as interval) where idempotency_key = ?")
                .params(claimedAt, key)
                .update()
        }

        interrupted("1 second")
        val held = create(body, key)
        assertEquals(409, held.statusCode.value())
        assertEquals("idempotency-request-in-progress", held.problem())

        interrupted("10 minutes")
        val takenOver = create(body, key)
        assertEquals(null, takenOver.headers.getFirst("Idempotent-Replay"))
        assertEquals(201, takenOver.statusCode.value())
        assertEquals(created.get("id").asString(), takenOver.json().get("id").asString())
        assertEquals(calls, operator.calls.get())
        val found = json.readTree(client.get().uri("/v1/payments?reference={r}", reference).retrieve().body(ByteArray::class.java)).get("data")
        assertEquals(created.get("id").asString(), found.get(0).get("id").asString())
        assertEquals("true", create(body, key).headers.getFirst("Idempotent-Replay"))
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
    fun `a replayed problem keeps its recorded body under a Request-Id of its own`() {
        val body = body()
        create(body)
        val key = UUID.randomUUID().toString()
        val conflict = create(body, key)
        val replay = create(body, key)
        assertEquals("true", replay.headers.getFirst("Idempotent-Replay"))
        assertContentEquals(conflict.body, replay.body)
        assertEquals(conflict.headers.getFirst("Request-Id"), conflict.json().get("request_id").asString())
        assertEquals(conflict.json().get("request_id").asString(), replay.json().get("request_id").asString())
        assertNotEquals(replay.json().get("request_id").asString(), replay.headers.getFirst("Request-Id"))
    }

    @Test
    fun `the gateway publishes its upstream data as metadata keys that never replace a client key`() {
        val reference = "INV-${UUID.randomUUID()}"
        val created = create(body(reference = reference).dropLast(1) + ""","metadata":{"order":"184"}}""").json()
        assertTrue(created.get("provider_order_id") == null && created.get("provider_reference") == null)
        val metadata = created.get("metadata")
        assertEquals("184", metadata.get("order").asString())
        assertEquals(orderIds.derive(MerchantAccount.DEFAULT, reference), metadata.get("gateway.order_id").asString())
        assertEquals(operator.paymentIdOf(orderIds.derive(MerchantAccount.DEFAULT, reference)), metadata.get("gateway.provider_reference").asString())

        val owned = create(body().dropLast(1) + ""","metadata":{"gateway.order_id":"mine"}}""").json().get("metadata")
        assertEquals("mine", owned.get("gateway.order_id").asString())
        assertTrue(owned.get("gateway.provider_reference").asString().startsWith("MA-"))
    }

    @Test
    fun `a signed callback settles the payment once and a contradiction is recorded`() {
        val payment = create(body()).json()
        val id = payment.get("id").asString()
        val orderId = payment.get("metadata").get("gateway.order_id").asString()

        assertEquals(204, deliver(operator.callback(orderId, "succeeded")))
        assertEquals("succeeded", read(id).get("status").asString())
        assertEquals(204, deliver(operator.callback(orderId, "succeeded")))
        assertEquals(0, conflicts(id))

        assertEquals(204, deliver(operator.callback(orderId, "failed", reason = "declined")))
        assertEquals("succeeded", read(id).get("status").asString())
        assertEquals(1, conflicts(id))
    }

    @Test
    fun `a settled payment reported again as succeeded for another amount is a conflict`() {
        val payment = create(body()).json()
        val id = payment.get("id").asString()
        val orderId = payment.get("metadata").get("gateway.order_id").asString()
        deliver(operator.callback(orderId, "succeeded"))
        assertEquals(204, deliver(operator.callback(orderId, "succeeded", amount = 124_900)))
        assertEquals("succeeded", read(id).get("status").asString())
        assertEquals(1, conflicts(id))
    }

    @Test
    fun `a callback with a wrong signature has no effect`() {
        val payment = create(body()).json()
        val callback = operator.callback(payment.get("metadata").get("gateway.order_id").asString(), "succeeded")
        assertEquals(401, deliver(callback, signature = "sig1=:AAAA:"))
        val tampered = callback.copy(body = callback.body.replace("125000", "125001"))
        assertEquals(401, deliver(tampered))
        assertEquals("pending", read(payment.get("id").asString()).get("status").asString())
    }

    @Test
    fun `a replayed operator callback is refused through the nonce store shared in PostgreSQL`() {
        val payment = create(body()).json()
        val callback = operator.callback(payment.get("metadata").get("gateway.order_id").asString(), "succeeded")
        assertEquals(204, deliver(callback))
        val nonce = Regex("nonce=\"([^\"]+)\"").find(callback.headers.getValue("Signature-Input"))!!.groupValues[1]
        assertEquals(1, storedNonces(nonce))
        assertEquals(401, deliver(callback))
    }

    @Test
    fun `a released nonce can be reserved again and an expired one is purged`() {
        val nonce = "nonce-${UUID.randomUUID()}"
        val until = Instant.now().plusSeconds(300)
        assertTrue(nonces.reserve(nonce, until))
        assertFalse(nonces.reserve(nonce, until))
        nonces.release(nonce)
        assertEquals(0, storedNonces(nonce))
        assertTrue(nonces.reserve(nonce, until))

        val expired = "nonce-${UUID.randomUUID()}"
        jdbc.sql("insert into webhook_nonces (nonce, expires_at) values (?, now() - interval '1 minute')").param(expired).update()
        noncePurge.run()
        assertEquals(0, storedNonces(expired))
        assertEquals(1, storedNonces(nonce))
    }

    @Test
    fun `an expired nonce left before the purge does not block a new reservation`() {
        val nonce = "nonce-${UUID.randomUUID()}"
        jdbc.sql("insert into webhook_nonces (nonce, expires_at) values (?, now() - interval '1 minute')").param(nonce).update()
        assertTrue(nonces.reserve(nonce, Instant.now().plusSeconds(300)))
    }

    private fun storedNonces(nonce: String) = jdbc.sql("select count(*) from webhook_nonces where nonce = ?").param(nonce).query(Int::class.java).single()

    @Test
    fun `a success for another amount keeps the payment pending`() {
        val payment = create(body()).json()
        val id = payment.get("id").asString()
        deliver(operator.callback(payment.get("metadata").get("gateway.order_id").asString(), "succeeded", amount = 124_900))
        assertEquals("pending", read(id).get("status").asString())
        assertEquals(1, conflicts(id))
    }

    @Test
    fun `reading against an unknown operator status or another amount answers 200 with the payment unchanged`() {
        listOf("in_review" to null, "succeeded" to 124_900L).forEach { (status, amount) ->
            val payment = create(body()).json()
            val id = payment.get("id").asString()
            val before = read(id)
            operator.statuses[payment.get("metadata").get("gateway.order_id").asString()] = status
            amount?.let { operator.amounts[payment.get("metadata").get("gateway.order_id").asString()] = it }
            val reread = client.get().uri("/v1/payments/{id}", id).retrieve().toEntity(ByteArray::class.java)
            assertEquals(200, reread.statusCode.value(), status)
            assertEquals(before, reread.json(), status)
        }
    }

    @Test
    fun `reading a terminal payment does not call the operator`() {
        val payment = create(body()).json()
        val id = payment.get("id").asString()
        val orderId = payment.get("metadata").get("gateway.order_id").asString()
        operator.statuses[orderId] = "failed"
        val reread = json.readTree(client.get().uri("/v1/payments/{id}", id).retrieve().body(ByteArray::class.java))
        assertEquals("failed", reread.get("status").asString())
        assertEquals("unspecified", reread.get("failure_reason").asString())

        val calls = operator.calls.get()
        client.get().uri("/v1/payments/{id}", id).retrieve().toBodilessEntity()
        assertEquals(calls, operator.calls.get())
    }

    @Test
    fun `credentials echoed by the operator never reach the client`() {
        operator.failWith = 400
        operator.errorMessage = "Rejected request authorized with Bearer mock_alpha_test_key"
        val failed = create(body())
        assertEquals("provider-error", failed.problem())
        val message = failed.json().get("detail").asString()
        assertTrue(!message.contains("mock_alpha_test_key"), message)
        assertTrue(message.contains("[REDACTED]"))
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

    @Test
    fun `an operator 4xx answers provider-error without effect and leaves no payment behind`() {
        val reference = "INV-${UUID.randomUUID()}"
        operator.failWith = 422
        operator.errorMessage = "Subscriber not registered"
        val failed = create(body(reference = reference))
        assertEquals(502, failed.statusCode.value())
        assertEquals("provider-error", failed.problem())
        assertEquals("none", failed.json().get("effect").asString())
        assertEquals("internal-error: Subscriber not registered", failed.json().get("detail").asString())
        assertEquals(0, jdbc.sql("select count(*) from payments where reference = ?").param(reference).query(Int::class.java).single())

        operator.failWith = null
        assertEquals(201, create(body(reference = reference)).statusCode.value())
    }

    @Test
    fun `every authentication failure answers unauthenticated with the same title`() {
        val revoked = keys.issue(MerchantAccount.DEFAULT, "tests", setOf(Scope.PAYMENTS_READ))
        keys.revoke(revoked.keyId)
        val failures =
            listOf(
                null,
                "Basic abc",
                "Bearer ofsp_test_short",
                "Bearer ofsp_live_" + "A".repeat(40),
                "Bearer ofsp_test_" + "A".repeat(40),
                "Bearer ${revoked.secret}",
            ).map(::readAs)
        failures.forEach { response ->
            assertEquals(401, response.statusCode.value())
            assertEquals("Bearer", response.headers.getFirst("WWW-Authenticate"))
            assertEquals("unauthenticated", response.problem())
        }
        assertEquals(1, failures.map { it.json().get("title").asString() }.toSet().size)
    }

    @Test
    fun `a key without the scope is forbidden whether the payment exists or not`() {
        val limited = keys.issue(MerchantAccount.DEFAULT, "tests", setOf(Scope.CAPABILITIES_READ)).secret
        val existing = create(body()).json().get("id").asString()
        assertEquals("forbidden", readAs("Bearer $limited", existing).problem())
        assertEquals("forbidden", readAs("bearer $limited", "pay_unknown").problem())
        assertEquals(200, readAs("Bearer $key", existing).statusCode.value())
    }

    @Test
    fun `the discovery document lists the endpoints and reveals no provider`() {
        val raw = anonymous.get().uri("/.well-known/openfsp-configuration").retrieve().body(String::class.java)!!
        val configuration = json.readTree(raw)
        assertEquals("http://127.0.0.1:8080", configuration.get("issuer").asString())
        assertEquals("http://127.0.0.1:8080/v1/payments", configuration.get("payments_endpoint").asString())
        assertEquals("http://127.0.0.1:8080/v1/capabilities", configuration.get("capabilities_endpoint").asString())
        assertTrue(configuration.get("webhook_keys").items().isNotEmpty())
        assertEquals(listOf("ed25519"), configuration.get("webhook_signature_algorithms_supported").items().map { it.asString() })
        assertEquals(
            listOf("payments", "payments.fee", "payments.lookup", "payments.statement", "webhooks.emit"),
            configuration.get("capabilities_supported").items().map { it.asString() },
        )
        assertEquals("OKP", configuration.get("webhook_keys").items().first().get("kty").asString())
        assertTrue(!raw.contains("mock_alpha") && !raw.contains("/admin"))
    }

    @Test
    fun `capabilities are announced per provider to every key`() {
        val reader = keys.issue(MerchantAccount.DEFAULT, "tests", setOf(Scope.CAPABILITIES_READ)).secret
        val response = anonymous.get().uri("/v1/capabilities").header("Authorization", "Bearer $reader").retrieve().toEntity(ByteArray::class.java)
        val provider = response.json().get("providers").get(0)
        assertEquals("mock_alpha", provider.get("id").asString())
        assertEquals(
            listOf("payments", "payments.fee", "payments.lookup", "payments.statement", "webhooks.emit"),
            provider.get("capabilities").items().map { it.asString() },
        )
        assertEquals(1000, provider.get("payments").get("limits").get("min_amount").get("amount").asInt())
        assertTrue(response.headers.cacheControl!!.contains("max-age"))
        assertEquals(200, client.get().uri("/v1/capabilities").retrieve().toEntity(ByteArray::class.java).statusCode.value())
    }

    @Test
    fun `an amount outside the declared bounds is rejected before calling the operator`() {
        val calls = operator.calls.get()
        val tooSmall = create(body(amount = 999))
        assertEquals("amount-out-of-range", tooSmall.problem())
        assertEquals(7_500_000, tooSmall.json().get("max_amount").get("amount").asInt())
        assertEquals("amount-out-of-range", create(body(amount = 7_500_100)).problem())
        assertEquals("amount-out-of-range", create(body(amount = 100_050)).problem())
        assertEquals(calls, operator.calls.get())
        assertEquals(201, create(body(amount = 1000)).statusCode.value())
        assertEquals(201, create(body(amount = 7_500_000)).statusCode.value())
    }

    @Test
    fun `a payment still pending after its expiry stays pending while the provider guarantees no expiry`() {
        operator.expiresIn = 0
        val id = create(body()).json().get("id").asString()
        Thread.sleep(5)
        val reread = json.readTree(client.get().uri("/v1/payments/{id}", id).retrieve().body(ByteArray::class.java))
        assertEquals("pending", reread.get("status").asString())
    }

    @Test
    fun `the background reconciliation settles a payment without callback`() {
        val payment = create(body()).json()
        val id = payment.get("id").asString()
        operator.statuses[payment.get("metadata").get("gateway.order_id").asString()] = "succeeded"
        jdbc.sql("update payments set updated_at = now() - interval '1 minute' where id = ?").param(id).update()
        payments.reconcile(500)
        assertEquals("succeeded", read(id).get("status").asString())
    }

    @Test
    fun `a signed callback on the payment URL settles it once and a wrong token or a missing signature has no effect`() {
        val payment = create(body(provider = "mock_epsilon")).json()
        val orderId = payment.get("metadata").get("gateway.order_id").asString()
        val callbackUrl = URI(operator.callbackUrls.getValue(orderId))
        assertTrue(Regex("^/callbacks/mock_epsilon/[A-Za-z0-9_-]{22}$").matches(callbackUrl.path), callbackUrl.path)
        val signed = operator.callback(orderId, "succeeded", operator = "mock_epsilon")
        val post = { path: String, headers: Map<String, String> ->
            anonymous.post().uri(path).contentType(MediaType.APPLICATION_JSON).headers { h -> headers.forEach(h::set) }.body(signed.body).retrieve().toBodilessEntity().statusCode.value()
        }

        assertEquals(404, post("/callbacks/mock_epsilon/" + "A".repeat(22), signed.headers))
        assertEquals(404, post(callbackUrl.path, emptyMap()))
        assertEquals("pending", read(payment.get("id").asString()).get("status").asString())
        assertEquals(204, post(callbackUrl.path, signed.headers))
        assertEquals("succeeded", read(payment.get("id").asString()).get("status").asString())
        assertEquals(404, post(callbackUrl.path, signed.headers))
    }

    private val statementReader by lazy { keys.issue(MerchantAccount.DEFAULT, "tests", setOf(Scope.STATEMENTS_READ)).secret }

    private fun statement(
        query: String,
        path: String = "/v1/statements",
    ) = anonymous.get().uri("$path?$query").header("Authorization", "Bearer $statementReader").retrieve().toEntity(ByteArray::class.java)

    private fun settledAt(
        completedAt: String,
        status: String = "succeeded",
        amount: Long = 125_000,
        reference: String = "INV-${UUID.randomUUID()}",
    ): JsonNode {
        val payment = create(body(reference = reference, amount = amount)).json()
        val reason = "declined".takeIf { status == "failed" }
        assertEquals(204, deliver(operator.callback(payment.get("metadata").get("gateway.order_id").asString(), status, amount = amount, reason = reason)))
        val at = OffsetDateTime.parse(completedAt)
        jdbc.sql("update payments set completed_at = ?, booked_at = case when status = 'succeeded' then ? end where id = ?").params(at, at, payment.get("id").asString()).update()
        return payment
    }

    private fun ResponseEntity<ByteArray>.payments(): List<String> = json().get("data").items().map { it.get("payment").asString() }

    @Test
    fun `a statement entry is booked at the time the provider completed the payment`() {
        val payment = create(body()).json()
        val orderId = payment.get("metadata").get("gateway.order_id").asString()
        operator.completions[orderId] = Instant.parse("2025-06-02T14:00:00Z")
        assertEquals(204, deliver(operator.callback(orderId, "succeeded")))

        val entry = statement("provider=mock_alpha&business_date=2025-06-02").json().get("data").items().single { it.get("payment").asString() == payment.get("id").asString() }
        assertEquals("2025-06-02T14:00:00Z", entry.get("booked_at").asString())
        assertNotEquals("2025-06-02T14:00:00Z", read(payment.get("id").asString()).get("completed_at").asString())
    }

    @Test
    fun `the statement lists the succeeded payments of a business day in booking order`() {
        val later = settledAt("2025-03-10T15:00:00Z", amount = 50_000)
        val earlier = settledAt("2025-03-10T14:00:00Z")
        settledAt("2025-03-10T14:30:00Z", status = "failed")
        create(body())

        val read = statement("provider=mock_alpha&business_date=2025-03-10")

        assertEquals(200, read.statusCode.value())
        assertEquals(setOf("data"), read.json().propertyNames().toSet())
        assertEquals(listOf(earlier, later).map { it.get("id").asString() }, read.payments())
        val entry = read.json().get("data").get(1)
        assertEquals(setOf("provider", "business_date", "booked_at", "amount", "payment", "reference"), entry.propertyNames().toSet())
        assertEquals("mock_alpha", entry.get("provider").asString())
        assertEquals("2025-03-10", entry.get("business_date").asString())
        assertEquals("2025-03-10T15:00:00Z", entry.get("booked_at").asString())
        assertEquals(50_000L, entry.get("amount").get("amount").asLong())
        assertEquals(later.get("reference").asString(), entry.get("reference").asString())
        assertEquals(json.readTree("""{"data":[]}"""), statement("provider=mock_alpha&business_date=2025-03-09").json())
        assertEquals("forbidden", client.get().uri("/v1/statements?provider=mock_alpha&business_date=2025-03-10").retrieve().toEntity(ByteArray::class.java).problem())
        assertEquals("unauthenticated", anonymous.get().uri("/v1/statements?provider=mock_alpha&business_date=2025-03-10").retrieve().toEntity(ByteArray::class.java).problem())
        assertEquals(404, statement("provider=mock_alpha", "/v1/statement" + "_entries").statusCode.value())
        assertEquals(404, statement("provider=mock_alpha&business_date=2025-03-10", "/gateway/statements").statusCode.value())
    }

    @Test
    fun `the business day of an entry follows Port-au-Prince time through daylight saving`() {
        val beforeSummerMidnight = settledAt("2025-07-01T03:30:00Z").get("id").asString()
        val afterSummerMidnight = settledAt("2025-07-01T04:30:00Z").get("id").asString()
        val beforeWinterMidnight = settledAt("2025-01-15T04:30:00Z").get("id").asString()

        assertEquals(listOf(beforeSummerMidnight), statement("provider=mock_alpha&business_date=2025-06-30").payments())
        assertEquals(listOf(afterSummerMidnight), statement("provider=mock_alpha&business_date=2025-07-01").payments())
        val winter = statement("provider=mock_alpha&business_date=2025-01-14")
        assertEquals(listOf(beforeWinterMidnight), winter.payments())
        assertEquals("2025-01-14", winter.json().get("data").get(0).get("business_date").asString())
        assertTrue(statement("provider=mock_alpha&business_date=2025-01-15").payments().isEmpty())
    }

    @Test
    fun `a range returns the entries of its days, both ends included, page by page`() {
        val first = settledAt("2025-05-01T12:00:00Z").get("id").asString()
        val sameInstant = listOf(settledAt("2025-05-02T12:00:00Z"), settledAt("2025-05-02T12:00:00Z")).map { it.get("id").asString() }.sorted()
        val last = settledAt("2025-05-03T12:00:00Z").get("id").asString()
        settledAt("2025-04-30T12:00:00Z")
        settledAt("2025-05-04T12:00:00Z")
        val range = "provider=mock_alpha&from=2025-05-01&to=2025-05-03"

        val pageOne = statement("$range&limit=3")
        val cursor = pageOne.json().get("next_cursor").asString()
        val pageTwo = statement("$range&limit=3&cursor=$cursor")

        assertEquals(listOf(first) + sameInstant, pageOne.payments())
        assertEquals(listOf(last), pageTwo.payments())
        assertFalse(pageTwo.json().has("next_cursor"))
        assertEquals(listOf(first) + sameInstant + last, statement(range).payments())
        assertFalse(statement("$range&limit=4").json().has("next_cursor"))
        assertEquals(listOf(first), statement("provider=mock_alpha&from=2025-05-01&to=2025-05-01").payments())
    }

    @Test
    fun `a statement request without a provider or a period, or with an unreadable one, is refused`() {
        val refusal = { query: String -> statement(query).let { Triple(it.statusCode.value(), it.problem(), it.json().get("errors").get(0).get("field").asString()) } }

        assertEquals(Triple(400, "missing-field", "/provider"), refusal("business_date=2025-03-10"))
        assertEquals(Triple(400, "missing-field", "/business_date"), refusal("provider=mock_alpha"))
        assertEquals(Triple(422, "invalid-field", "/to"), refusal("provider=mock_alpha&from=2025-03-01"))
        assertEquals(Triple(422, "invalid-field", "/from"), refusal("provider=mock_alpha&to=2025-03-01"))
        assertEquals(Triple(422, "invalid-field", "/business_date"), refusal("provider=mock_alpha&business_date=2025-03-01&from=2025-03-01&to=2025-03-02"))
        assertEquals(Triple(422, "invalid-field", "/business_date"), refusal("provider=mock_alpha&business_date=tomorrow"))
        assertEquals(Triple(422, "invalid-field", "/from"), refusal("provider=mock_alpha&from=2025-02-30&to=2025-03-02"))
        assertEquals(Triple(422, "invalid-field", "/to"), refusal("provider=mock_alpha&from=2025-03-02&to=2025-03-01"))
        assertEquals(Triple(422, "invalid-field", "/to"), refusal("provider=mock_alpha&from=2025-03-01&to=2025-04-01"))
        assertEquals(200, statement("provider=mock_alpha&from=2025-03-01&to=2025-03-31").statusCode.value())
        listOf("0", "101", "many").forEach { assertEquals(Triple(422, "invalid-field", "/limit"), refusal("provider=mock_alpha&business_date=2025-03-10&limit=$it"), it) }
        assertEquals(200, statement("provider=mock_alpha&business_date=2025-03-10&limit=100").statusCode.value())
        assertEquals(Triple(422, "invalid-field", "/cursor"), refusal("provider=mock_alpha&business_date=2025-03-10&cursor=elsewhere"))
        assertEquals(Triple(422, "invalid-field", "/provider"), refusal("provider=unknown_provider&business_date=2025-03-10"))
    }

    @Test
    fun `the statement capability is declared only for a provider that provides statements and its absence refuses the statement`() {
        val capabilities = json.readTree(client.get().uri("/v1/capabilities").retrieve().body(ByteArray::class.java))
        val declaring = capabilities.get("providers").items().filter { provider -> provider.get("capabilities").items().any { it.asString() == "payments.statement" } }
        assertEquals(listOf("mock_alpha"), declaring.map { it.get("id").asString() })
        val refused = statement("provider=mock_epsilon&business_date=2025-08-04")
        assertEquals(422, refused.statusCode.value())
        assertEquals("capability-not-supported", refused.problem())
    }

    @Test
    fun `the admin reads the statement as the public endpoint serves it`() {
        val ids = listOf("2025-08-04T12:00:00Z", "2025-08-05T12:00:00Z").map { settledAt(it).get("id").asString() }
        val admin = { query: String -> anonymous.get().uri("/admin/statements?$query").retrieve().toEntity(ByteArray::class.java) }

        val day = admin("provider=mock_alpha&business_date=2025-08-04")
        assertEquals(statement("provider=mock_alpha&business_date=2025-08-04").json(), day.json())
        assertEquals(listOf(ids.first()), day.payments())
        val pageOne = admin("provider=mock_alpha&from=2025-08-04&to=2025-08-05&limit=1")
        assertEquals(listOf(ids.first()), pageOne.payments())
        assertEquals(listOf(ids.last()), admin("provider=mock_alpha&from=2025-08-04&to=2025-08-05&limit=1&cursor=${pageOne.json().get("next_cursor").asString()}").payments())
        assertEquals("missing-field", admin("provider=mock_alpha").problem())
        assertEquals("invalid-field", admin("provider=mock_alpha&from=2025-08-01&to=2025-09-01").problem())
        assertEquals("invalid-field", admin("provider=unknown_provider&business_date=2025-08-04").problem())
    }

    @Test
    fun `the admin exports the whole statement of a period as CSV`() {
        val suffix = UUID.randomUUID().toString()
        val formula = settledAt("2025-09-02T12:00:00Z", amount = 50_000, reference = "-SUM/A1:$suffix")
        val plain = settledAt("2025-09-01T16:00:00Z")
        settledAt("2025-09-01T17:00:00Z", status = "failed")
        val export = { query: String -> anonymous.get().uri("/admin/statements/export?$query").retrieve().toEntity(ByteArray::class.java) }

        val range = export("provider=mock_alpha&from=2025-09-01&to=2025-09-02")

        assertEquals(200, range.statusCode.value())
        assertEquals(MediaType.parseMediaType("text/csv; charset=utf-8"), range.headers.contentType)
        assertEquals("attachment; filename=\"statement-mock_alpha-2025-09-01-2025-09-02.csv\"", range.headers.getFirst("Content-Disposition"))
        assertEquals(
            listOf(
                "business_date,booked_at,provider,payment,reference,amount,currency,fee_amount,fee_bearer",
                "2025-09-01,2025-09-01T16:00:00Z,mock_alpha,${plain.get("id").asString()},${plain.get("reference").asString()},1250.00,HTG,,",
                "2025-09-02,2025-09-02T12:00:00Z,mock_alpha,${formula.get("id").asString()},'-SUM/A1:$suffix,500.00,HTG,,",
                "",
            ).joinToString("\r\n"),
            String(range.body!!, Charsets.UTF_8),
        )
        val day = export("provider=mock_alpha&business_date=2025-09-01")
        assertEquals("attachment; filename=\"statement-mock_alpha-2025-09-01-2025-09-01.csv\"", day.headers.getFirst("Content-Disposition"))
        assertEquals(2, String(day.body!!, Charsets.UTF_8).trimEnd().lines().size)
        assertEquals("missing-field", export("provider=mock_alpha").problem())
        assertEquals("invalid-field", export("provider=mock_alpha&from=2025-09-01&to=2025-10-02").problem())
        assertEquals("invalid-field", export("provider=unknown_provider&business_date=2025-09-01").problem())
    }

    @Test
    fun `the payer return point redirects to the recorded return URL only`() {
        val reference = "INV-${UUID.randomUUID()}"
        val payment = create(body(reference = reference, provider = "mock_epsilon")).json()
        val noRedirects = anonymous.mutate().requestFactory(
            org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NEVER).build()),
        ).build()
        val back =
            noRedirects
                .get()
                .uri("/return/mock_epsilon?order_id={id}&return_url=https://attacker.example", payment.get("metadata").get("gateway.order_id").asString())
                .retrieve()
                .toBodilessEntity()
        assertEquals(303, back.statusCode.value())
        assertEquals("https://shop.example/orders/$reference", back.headers.location.toString())
        assertEquals(404, noRedirects.get().uri("/return/mock_epsilon?order_id=unknown").retrieve().toBodilessEntity().statusCode.value())
        assertEquals(404, noRedirects.get().uri("/return/mock_epsilon").retrieve().toBodilessEntity().statusCode.value())
    }

    @Test
    fun `the creation reaches the operator in the OpenFSP shape with a key derived from the order`() {
        val reference = "INV-${UUID.randomUUID()}"
        val orderId = create(body(reference = reference)).json().get("metadata").get("gateway.order_id").asString()
        val (headers, sent) = operator.requests.last()
        assertEquals("create-$orderId", headers["idempotency-key"])
        assertEquals("Bearer mock_alpha_test_key", headers["authorization"])
        val request = json.readTree(sent)
        assertEquals(orderId, request.get("reference").asString())
        assertEquals("mock_alpha", request.get("provider").asString())
        assertEquals(125000, request.get("amount").get("amount").asInt())
        assertEquals("+50934567890", request.get("payer").get("phone_number").asString())
        assertEquals("https://shop.example/orders/$reference", request.get("return_url").asString())
        assertTrue(request.get("callback_url") == null)
    }

    @Test
    fun `a payment of unknown creation outcome is shown pending without next action and announced once`() {
        val reference = "INV-${UUID.randomUUID()}"
        operator.failWith = 500
        assertEquals("unknown", create(body(reference = reference, provider = "mock_epsilon")).json().get("effect").asString())
        operator.failWith = null
        val id = jdbc.sql("select id from payments where reference = ?").param(reference).query(String::class.java).single()

        val found = json.readTree(client.get().uri("/v1/payments?reference={reference}", reference).retrieve().body(ByteArray::class.java))
        assertEquals(id, found.get("data").get(0).get("id").asString())
        assertEquals("pending", found.get("data").get(0).get("status").asString())
        val payment = read(id)
        assertEquals("pending", payment.get("status").asString())
        assertTrue(payment.get("next_action") == null || payment.get("next_action").isNull)
        await { webhooksFor(id).size == 1 }
        assertEquals("payment.created", json.readTree(webhooksFor(id).single().body).get("type").asString())
    }

    @Test
    fun `a payment whose creation is in flight is not shown`() {
        val reference = "INV-${UUID.randomUUID()}"
        val id = ResourceIds.generate("pay")
        jdbc
            .sql(
                "insert into payments (id, account_id, reference, status, amount, currency, provider, provider_order_id, created_at, updated_at) " +
                    "values (?, 'acct_default', ?, 'pending', 125000, 'HTG', 'mock_epsilon', ?, now(), now())",
            ).params(id, reference, UUID.randomUUID().toString())
            .update()

        val found = json.readTree(client.get().uri("/v1/payments?reference={reference}", reference).retrieve().body(ByteArray::class.java))
        assertEquals(0, found.get("data").size())
        assertEquals(404, client.get().uri("/v1/payments/{id}", id).retrieve().toBodilessEntity().statusCode.value())
    }

    @Test
    fun `a payment captured by the operator whose answer was lost is settled by its callback`() {
        val reference = "INV-${UUID.randomUUID()}"
        operator.loseCreationAnswers = true
        assertEquals("unknown", create(body(reference = reference)).json().get("effect").asString())
        operator.loseCreationAnswers = false
        val orderId = jdbc.sql("select provider_order_id from payments where reference = ?").param(reference).query(String::class.java).single()

        assertEquals(204, deliver(operator.callback(orderId, "succeeded")))
        val found = json.readTree(client.get().uri("/v1/payments?reference={reference}", reference).retrieve().body(ByteArray::class.java))
        assertEquals("succeeded", found.get("data").get(0).get("status").asString())
    }

    @Test
    fun `a retry after a lost answer looks the order up before creating again`() {
        val key = UUID.randomUUID().toString()
        val reference = "INV-${UUID.randomUUID()}"
        val body = body(reference = reference)
        operator.loseCreationAnswers = true
        assertEquals("unknown", create(body, key).json().get("effect").asString())
        operator.loseCreationAnswers = false
        val orderId = jdbc.sql("select provider_order_id from payments where reference = ?").param(reference).query(String::class.java).single()
        operator.statuses[orderId] = "succeeded"

        val calls = operator.calls.get()
        val retried = create(body, key)
        assertEquals(201, retried.statusCode.value())
        assertEquals("succeeded", retried.json().get("status").asString())
        assertEquals(calls + 1, operator.calls.get())
    }

    @Test
    fun `reading one payment too often stops calling the operator and answers the last known state`() {
        val id = create(body()).json().get("id").asString()
        val calls = operator.calls.get()
        val statuses = (1..4).map { client.get().uri("/v1/payments/{id}", id).retrieve().toEntity(ByteArray::class.java) }
        assertEquals(List(4) { 200 }, statuses.map { it.statusCode.value() })
        assertEquals(calls + 3, operator.calls.get())
    }

    @Test
    fun `a merchant account never sees the payments of another account and reuses its references freely`() {
        val first = accountKey("first")
        val second = accountKey("second")
        val reference = "INV-${UUID.randomUUID()}"
        val created = createAs(first, body(reference))
        assertEquals(201, created.statusCode.value())
        val id = created.json().get("id").asString()

        assertEquals(200, readAs("Bearer $first", id).statusCode.value())
        assertEquals(404, readAs("Bearer $second", id).statusCode.value())
        assertEquals(0, byReferenceAs(second, reference).size())
        assertEquals(1, byReferenceAs(first, reference).size())

        val reused = createAs(second, body(reference))
        assertEquals(201, reused.statusCode.value())
        assertNotEquals(id, reused.json().get("id").asString())
        assertNotEquals(created.json().get("metadata").get("gateway.order_id"), reused.json().get("metadata").get("gateway.order_id"))
        assertEquals(id, byReferenceAs(first, reference).get(0).get("id").asString())
    }

    @Test
    fun `the admin opens a merchant account, lists it and issues a key bound to it`() {
        val name = "admin-${UUID.randomUUID().toString().take(8)}"
        val opened = anonymous.post().uri("/admin/accounts").contentType(MediaType.APPLICATION_JSON).body("""{"name":"$name"}""").retrieve().toEntity(ByteArray::class.java)
        assertEquals(201, opened.statusCode.value())
        val account = opened.json().get("id").asString()
        val taken = anonymous.post().uri("/admin/accounts").contentType(MediaType.APPLICATION_JSON).body("""{"name":"$name"}""").retrieve().toEntity(ByteArray::class.java)
        assertEquals(422, taken.statusCode.value())

        val listed = json.readTree(anonymous.get().uri("/admin/accounts").retrieve().body(ByteArray::class.java)).get("data").items()
        assertTrue(listed.any { it.get("id").asString() == account && it.get("name").asString() == name })
        anonymous.post().uri("/admin/keys").contentType(MediaType.APPLICATION_JSON)
            .body("""{"account":"$account","principal":"web","scopes":["payments:read"]}""").retrieve().toBodilessEntity()
        val keysOfAccount = json.readTree(anonymous.get().uri("/admin/keys?account={account}", account).retrieve().body(ByteArray::class.java)).get("data").items()
        assertEquals(listOf(account), keysOfAccount.map { it.get("account").asString() })
        assertEquals(0, json.readTree(anonymous.get().uri("/admin/payments?account={account}", account).retrieve().body(ByteArray::class.java)).get("data").size())
    }

    @Test
    fun `an account stores its own provider credentials, sealed and write-only, and only then sees the service`() {
        val account = accounts.open("wallet-${UUID.randomUUID().toString().take(8)}").id
        val secret = keys.issue(account, "shop", setOf(Scope.CAPABILITIES_READ)).secret
        val credentials = "/admin/accounts/$account/providers/mock_wallet/credentials"
        val providerIds = {
            json.readTree(anonymous.get().uri("/v1/capabilities").header("Authorization", "Bearer $secret").retrieve().body(ByteArray::class.java))
                .get("providers").items().map { it.get("id").asString() }
        }
        val walletOf = {
            json.readTree(anonymous.get().uri("/admin/accounts/$account/providers").retrieve().body(ByteArray::class.java))
                .get("data").items().first { it.get("provider").asString() == "mock_wallet" }
        }
        val put = { body: String -> anonymous.put().uri(credentials).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(ByteArray::class.java) }

        assertFalse("mock_wallet" in providerIds())
        assertEquals("none", walletOf().get("source").asString())
        assertEquals(400, put("""{"credentials":{"client-id":"wallet-client"}}""").statusCode.value())
        assertEquals(422, put("""{"credentials":{"client-id":"wallet-client","client-secret":"wallet-secret-value","extra":"x"}}""").statusCode.value())
        assertEquals(204, put("""{"credentials":{"client-id":"wallet-client","client-secret":"wallet-secret-value"}}""").statusCode.value())

        val wallet = walletOf()
        assertEquals("account", wallet.get("source").asString())
        assertTrue(wallet.get("notification_url").asString().endsWith("/callbacks/mock_wallet/accounts/$account"))
        assertFalse(wallet.toString().contains("wallet-secret-value"))
        assertTrue("mock_wallet" in providerIds())
        val stored = jdbc.sql("select ciphertext from provider_credential_revisions where account_id = ?").param(account).query(ByteArray::class.java).single()
        assertFalse(String(stored, Charsets.ISO_8859_1).contains("wallet-secret-value"))
        assertEquals(404, anonymous.put().uri("/admin/accounts/$account/providers/mock_alpha/credentials").contentType(MediaType.APPLICATION_JSON).body("""{"credentials":{}}""").retrieve().toBodilessEntity().statusCode.value())

        assertEquals(204, anonymous.delete().uri(credentials).retrieve().toBodilessEntity().statusCode.value())
        assertEquals("none", walletOf().get("source").asString())
        assertFalse("mock_wallet" in providerIds())
    }

    @Test
    fun `a rotation keeps the previous revision retired, a revocation wipes it and stays within its account`() {
        val account = accounts.open("revisions-${UUID.randomUUID().toString().take(8)}").id
        val other = accounts.open("intruder-${UUID.randomUUID().toString().take(8)}").id
        val put = {
            anonymous.put().uri("/admin/accounts/$account/providers/mock_wallet/credentials").contentType(MediaType.APPLICATION_JSON)
                .body("""{"credentials":{"client-id":"wallet-client","client-secret":"wallet-secret-${UUID.randomUUID()}"}}""").retrieve().toBodilessEntity()
        }
        val walletOf = { owner: String ->
            json.readTree(anonymous.get().uri("/admin/accounts/$owner/providers").retrieve().body(ByteArray::class.java))
                .get("data").items().first { it.get("provider").asString() == "mock_wallet" }
        }
        val revoke = { owner: String, revision: String ->
            anonymous.post().uri("/admin/accounts/$owner/providers/mock_wallet/revisions/$revision/revoke").retrieve().toEntity(ByteArray::class.java).statusCode.value()
        }
        put()
        put()

        val revisions = walletOf(account).get("revisions").items().toList()
        assertEquals(listOf("active", "retired"), revisions.map { it.get("status").asString() })
        assertTrue(revisions.all { it.get("sealed").asBoolean() })
        val (active, retired) = revisions.map { it.get("id").asString() }

        assertEquals(404, revoke(other, retired))
        assertEquals("retired", walletOf(account).get("revisions").items().last().get("status").asString())
        assertEquals(204, revoke(account, retired))
        assertEquals(409, revoke(account, retired))
        val wiped = jdbc.sql("select count(*) from provider_credential_revisions where id = ? and status = 'revoked' and ciphertext is null").param(retired).query(Int::class.java).single()
        assertEquals(1, wiped)
        assertEquals("account", walletOf(account).get("source").asString())

        assertEquals(204, revoke(account, active))
        assertEquals("none", walletOf(account).get("source").asString())
        assertEquals(listOf("revoked", "revoked"), walletOf(account).get("revisions").items().map { it.get("status").asString() })
        val audited = json.readTree(anonymous.get().uri("/admin/audit?kind=provider_credentials_revoked&size=50").retrieve().body(ByteArray::class.java)).get("data").items()
        assertEquals(setOf("mock_wallet $retired", "mock_wallet $active"), audited.filter { it.get("result").asString() == account }.map { it.get("detail").asString() }.toSet())
    }

    @Test
    fun `an account switched to the platform credentials retires its own, is audited and leaves other accounts on theirs`() {
        val account = accounts.open("modes-${UUID.randomUUID().toString().take(8)}").id
        val other = accounts.open("keeps-${UUID.randomUUID().toString().take(8)}").id
        val put = { owner: String ->
            anonymous.put().uri("/admin/accounts/$owner/providers/mock_wallet/credentials").contentType(MediaType.APPLICATION_JSON)
                .body("""{"credentials":{"client-id":"wallet-client","client-secret":"wallet-secret-${UUID.randomUUID()}"}}""").retrieve().toBodilessEntity()
        }
        val walletOf = { owner: String ->
            json.readTree(anonymous.get().uri("/admin/accounts/$owner/providers").retrieve().body(ByteArray::class.java))
                .get("data").items().first { it.get("provider").asString() == "mock_wallet" }
        }
        val switch = { owner: String, mode: String ->
            anonymous.put().uri("/admin/accounts/$owner/providers/mock_wallet/mode").contentType(MediaType.APPLICATION_JSON)
                .body("""{"mode":"$mode"}""").retrieve().toEntity(ByteArray::class.java).statusCode.value()
        }
        assertEquals("platform", walletOf(account).get("mode").asString())
        put(account)
        put(other)
        assertEquals("own", walletOf(account).get("mode").asString())

        assertEquals(422, switch(account, "shared"))
        assertEquals(204, switch(account, "platform"))
        val switched = walletOf(account)
        assertEquals("platform" to "none", switched.get("mode").asString() to switched.get("source").asString())
        assertEquals(listOf("retired"), switched.get("revisions").items().map { it.get("status").asString() })
        assertEquals("own" to "account", walletOf(other).get("mode").asString() to walletOf(other).get("source").asString())
        assertEquals(409, switch(account, "own"))

        val audited = json.readTree(anonymous.get().uri("/admin/audit?kind=provider_credential_mode_changed&size=50").retrieve().body(ByteArray::class.java)).get("data").items()
        val revision = switched.get("revisions").items().single().get("id").asString()
        assertEquals(listOf("mock_wallet own platform $revision"), audited.filter { it.get("result").asString() == account }.map { it.get("detail").asString() })
        assertTrue(audited.none { it.get("result").asString() == other })
    }

    @Test
    fun `account credentials sealed under another application key give a clear problem instead of an internal error`() {
        val account = accounts.open("rekeyed-${UUID.randomUUID().toString().take(8)}").id
        val secret = keys.issue(account, "shop", setOf(Scope.PAYMENTS_WRITE)).secret
        val stored =
            anonymous.put().uri("/admin/accounts/$account/providers/mock_wallet/credentials").contentType(MediaType.APPLICATION_JSON)
                .body("""{"credentials":{"client-id":"wallet-client","client-secret":"wallet-secret-value"}}""").retrieve().toBodilessEntity()
        assertEquals(204, stored.statusCode.value())
        jdbc.sql("update provider_credential_revisions set key_id = 'sealedbyoldkey00' where account_id = ?").param(account).update()

        val refused =
            anonymous.post().uri("/v1/payments").header("Authorization", "Bearer $secret").header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).body(body(provider = "mock_wallet")).retrieve().toEntity(ByteArray::class.java)

        assertEquals(503, refused.statusCode.value())
        assertEquals("provider-unavailable", refused.problem())
        assertTrue(refused.json().get("detail").asString().contains("another application key"))
    }

    @Test
    fun `a key is issued only for an existing merchant account`() {
        val refused =
            anonymous.post().uri("/admin/keys").contentType(MediaType.APPLICATION_JSON)
                .body("""{"account":"acct_unknown","principal":"shop","scopes":["payments:read"]}""").retrieve().toEntity(ByteArray::class.java)
        assertEquals(422, refused.statusCode.value())
        assertEquals("/account", refused.json().get("errors").get(0).get("field").asString())
    }

    private fun accountKey(label: String): String {
        val account = accounts.open("isolation-$label-${UUID.randomUUID().toString().take(8)}")
        return keys.issue(account.id, "shop", setOf(Scope.PAYMENTS_READ, Scope.PAYMENTS_WRITE)).secret
    }

    private fun createAs(
        secret: String,
        body: String,
    ): ResponseEntity<ByteArray> =
        anonymous
            .post()
            .uri("/v1/payments")
            .header("Authorization", "Bearer $secret")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toEntity(ByteArray::class.java)

    private fun byReferenceAs(
        secret: String,
        reference: String,
    ): JsonNode = json.readTree(anonymous.get().uri("/v1/payments?reference={reference}", reference).header("Authorization", "Bearer $secret").retrieve().body(ByteArray::class.java)).get("data")

    @Test
    fun `the admin issues a key shown once and revokes it at once`() {
        val response =
            anonymous.post().uri("/admin/keys").contentType(MediaType.APPLICATION_JSON)
                .body("""{"account":"acct_default","principal":"shop","scopes":["payments:read"]}""").retrieve().toEntity(ByteArray::class.java)
        assertEquals("no-store", response.headers.cacheControl)
        val issued = json.readTree(response.body)
        val secret = issued.get("secret").asString()
        assertTrue(secret.startsWith("ofsp_test_"))
        val listed = json.readTree(anonymous.get().uri("/admin/keys").retrieve().body(ByteArray::class.java)).get("data").items()
        val summary = listed.first { it.get("id").asString() == issued.get("id").asString() }
        assertTrue(!summary.toString().contains(secret))
        assertEquals(404, readAs("Bearer $secret").statusCode.value())
        anonymous.post().uri("/admin/keys/{id}/revoke", issued.get("id").asString()).retrieve().toBodilessEntity()
        assertEquals("unauthenticated", readAs("Bearer $secret").problem())
        val keyIds = { uri: String -> json.readTree(anonymous.get().uri(uri).retrieve().body(ByteArray::class.java)).get("data").items().map { it.get("id").asString() } }
        assertTrue(issued.get("id").asString() !in keyIds("/admin/keys?size=100"))
        assertTrue(issued.get("id").asString() in keyIds("/admin/keys?status=revoked&q=shop&sort=principal&order=asc&size=100"))
        val lapsed =
            json.readTree(
                anonymous.post().uri("/admin/keys").contentType(MediaType.APPLICATION_JSON)
                    .body("""{"account":"acct_default","principal":"lapsed","scopes":["payments:read"],"expires_at":"2001-01-01T00:00:00Z"}""").retrieve().body(ByteArray::class.java),
            ).get("id").asString()
        assertTrue(lapsed !in keyIds("/admin/keys?size=100"))
        assertTrue(lapsed in keyIds("/admin/keys?status=expired&size=100"))
    }

    @Test
    fun `every transition is delivered as a signed webhook in sequence`() {
        val payment = create(body()).json()
        val id = payment.get("id").asString()
        deliver(operator.callback(payment.get("metadata").get("gateway.order_id").asString(), "succeeded"))
        await { webhooksFor(id).size == 2 }

        val verifier = verifier()
        val events = webhooksFor(id).map { json.readTree(it.body) }.sortedBy { it.get("sequence").asInt() }
        assertEquals(listOf("payment.created", "payment.succeeded"), events.map { it.get("type").asString() })
        assertEquals(listOf(1, 2), events.map { it.get("sequence").asInt() })
        assertEquals("pending", events[1].get("previous_status").asString())
        assertEquals("succeeded", events[1].get("data").get("status").asString())
        webhooksFor(id).forEach { webhook ->
            assertIs<Verification.Verified>(verifier.verify("POST", receiver.url, { webhook.headers[it.lowercase()] }, webhook.body))
        }
    }

    @Test
    fun `the admin view shows transitions and deliveries of a payment`() {
        val payment = create(body()).json()
        val id = payment.get("id").asString()
        deliver(operator.callback(payment.get("metadata").get("gateway.order_id").asString(), "succeeded"))
        await { webhooksFor(id).size == 2 }
        val detail = json.readTree(anonymous.get().uri("/admin/payments/{id}", id).retrieve().body(ByteArray::class.java))
        assertEquals(listOf("creation", "signed_callback"), detail.get("transitions").items().map { it.get("source").asString() })
        assertEquals(listOf("payment.created", "payment.succeeded"), detail.get("events").items().map { it.get("type").asString() })
        await {
            json.readTree(anonymous.get().uri("/admin/payments/{id}", id).retrieve().body(ByteArray::class.java))
                .get("events").all { event -> event.get("deliveries").all { it.get("status").asString() == "succeeded" } }
        }
    }

    @Test
    fun `an attestation settles a payment once and a later attestation is refused`() {
        val id = create(body()).json().get("id").asString()
        val attest = { status: String ->
            anonymous
                .post()
                .uri("/admin/payments/{id}/attestations", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"person":"ops@merchant.example","status":"$status","evidence":"statement 2026-11-09 line 12"}""")
                .retrieve()
                .body(ByteArray::class.java)
        }
        val detail = json.readTree(attest("succeeded"))
        assertEquals("succeeded", detail.get("payment").get("status").asString())
        assertEquals("attestation", detail.get("transitions").items().last().get("source").asString())
        assertEquals("https://openfsp.org/problems/state-conflict", json.readTree(attest("failed")).get("type").asString())
        assertEquals("succeeded", read(id).get("status").asString())
        assertEquals(0, conflicts(id))
    }

    @Test
    fun `an immediate rotation publishes and signs with the new key only`() {
        val rotated =
            json.readTree(anonymous.post().uri("/admin/webhooks/keys/rotate?immediate=true").retrieve().body(ByteArray::class.java)).get("kid").asString()
        val published = json.readTree(anonymous.get().uri("/.well-known/openfsp-configuration").retrieve().body(ByteArray::class.java)).get("webhook_keys").items()
        assertEquals(listOf(rotated), published.map { it.get("kid").asString() })

        val id = create(body()).json().get("id").asString()
        await { webhooksFor(id).isNotEmpty() }
        val webhook = webhooksFor(id).first()
        assertTrue(webhook.headers.getValue("signature-input").contains("keyid=\"$rotated\""))
        assertIs<Verification.Verified>(verifier().verify("POST", receiver.url, { webhook.headers[it.lowercase()] }, webhook.body))
        val endpoints = json.readTree(anonymous.get().uri("/admin/webhooks/endpoints").retrieve().body(ByteArray::class.java)).get("data").items()
        assertEquals(listOf("demo-shop", "paused-shop"), endpoints.map { it.get("name").asString() }.sorted())
    }

    @Test
    fun `requests and authentication failures are audited without secrets`() {
        val id = create(body()).json().get("id").asString()
        readAs("Bearer ofsp_test_" + "Z".repeat(40))
        val records = json.readTree(anonymous.get().uri("/admin/audit?limit=20").retrieve().body(ByteArray::class.java)).get("data").items()
        val created = records.first { it.get("operation").asString() == "POST /v1/payments" }
        assertEquals("request", created.get("kind").asString())
        assertEquals("201", created.get("result").asString())
        assertTrue(created.get("principal_id").asString().startsWith("prn_"))
        val exchange = created.get("exchange")
        assertEquals("[REDACTED]", exchange.get("request_headers").get("authorization").asString())
        assertTrue(exchange.get("request_body").asString().contains("amount"))
        assertTrue(exchange.get("response_body").asString().contains(id))
        assertTrue(exchange.get("duration_ms").asLong() >= 0)
        val failure = records.first { it.get("kind").asString() == "authentication_failure" }
        assertEquals("ZZZZZZZZZZZZ", failure.get("lookup_prefix").asString())
        assertEquals("401", failure.get("result").asString())
        assertTrue(records.none { it.toString().contains(key) })
        assertTrue(id.isNotEmpty())
    }

    @Test
    fun `the admin activity tells each payment's story with its source, and the overview counts today`() {
        val created = create(body()).json()
        val reference = created.get("reference").asString()
        val activity = json.readTree(anonymous.get().uri("/admin/activity?kind=transition&source=creation&size=50").retrieve().body(ByteArray::class.java)).get("data").items()
        val creation = activity.first { it.get("payment").get("reference").asString() == reference }
        assertEquals("transition", creation.get("kind").asString())
        assertEquals("creation", creation.get("source").asString())
        assertEquals("pending", creation.get("to_status").asString())
        assertEquals("mock_alpha", creation.get("payment").get("provider").asString())
        assertEquals("invalid-field", anonymous.get().uri("/admin/activity?kind=nonsense").retrieve().toEntity(ByteArray::class.java).problem())

        val overview = json.readTree(anonymous.get().uri("/admin/overview").retrieve().body(ByteArray::class.java))
        assertEquals("test", overview.get("environment").asString())
        assertTrue(overview.get("payments").items().any { it.get("status").asString() == "pending" && it.get("count").asLong() >= 1 })
    }

    @Test
    fun `the admin adds several webhooks, refuses bad ones and removes only those it added`() {
        val add = { body: String ->
            anonymous.post().uri("/admin/webhooks/endpoints").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(ByteArray::class.java)
        }
        assertEquals(201, add("""{"account":"acct_default","name":"erp","url":"http://127.0.0.1:9/erp","events":["payment.canceled"]}""").statusCode.value())
        assertEquals(201, add("""{"account":"acct_default","name":"accounting","url":"http://127.0.0.1:9/books","events":["payment.canceled","payment.expired"]}""").statusCode.value())
        assertEquals("invalid-field", add("""{"account":"acct_default","name":"erp","url":"http://127.0.0.1:9/again","events":["payment.created"]}""").problem())
        val admin = json.readTree(anonymous.get().uri("/admin/activity?kind=admin&size=50").retrieve().body(ByteArray::class.java)).get("data").items()
        assertTrue(admin.any { it.get("source").asString() == "webhook_added" && it.get("subject").asString() == "accounting" })
        assertEquals("invalid-field", add("""{"account":"acct_default","name":"bad","url":"http://127.0.0.1:9/x","events":["payment.unknown"]}""").problem())
        assertEquals("invalid-field", add("""{"account":"acct_default","name":"Bad Name","url":"http://127.0.0.1:9/x","events":["payment.created"]}""").problem())

        val endpoints = json.readTree(anonymous.get().uri("/admin/webhooks/endpoints").retrieve().body(ByteArray::class.java)).get("data").items()
        assertEquals(true, endpoints.single { it.get("name").asString() == "erp" }.get("managed").asBoolean())
        val configured = endpoints.first { !it.get("managed").asBoolean() }.get("name").asString()

        val remove = { name: String -> anonymous.delete().uri("/admin/webhooks/endpoints/{name}", name).retrieve().toBodilessEntity().statusCode.value() }
        assertEquals(204, remove("erp"))
        assertEquals(204, remove("accounting"))
        assertEquals(404, remove(configured))
    }

    @Test
    fun `the admin shows the discovery document and the capabilities together`() {
        val shown = json.readTree(anonymous.get().uri("/admin/configuration").retrieve().body(ByteArray::class.java))
        assertEquals("http://127.0.0.1:8080/v1/payments", shown.get("configuration").get("payments_endpoint").asString())
        assertTrue(shown.get("capabilities").get("providers").items().any { it.get("id").asString() == "mock_moncash" })
        val key = shown.get("configuration").get("webhook_keys").items().first()
        assertEquals("Ed25519", key.get("crv").asString())
        assertEquals(43, key.get("x").asString().length)
        assertEquals(90 * 24 * 60, shown.get("signing").get("rotate_every_minutes").asInt())
        assertTrue(shown.get("signing").get("next_rotation_at").asString().isNotBlank())
        assertEquals(null, rotation.rotateIfDue())
    }

    @Test
    fun `the admin pages and filters payments and the audit trail`() {
        val created = List(3) { create(body()).json() }
        val reference = created.last().get("reference").asString()
        val page = json.readTree(anonymous.get().uri("/admin/payments?size=2&page=0").retrieve().body(ByteArray::class.java))
        assertEquals(2, page.get("data").items().count())
        assertTrue(page.get("page").get("total_elements").asLong() >= 3)
        assertEquals(2, page.get("page").get("size").asInt())

        val found = json.readTree(anonymous.get().uri("/admin/payments?q={q}", reference.lowercase()).retrieve().body(ByteArray::class.java)).get("data").items()
        assertEquals(listOf(reference), found.map { it.get("reference").asString() })
        val byAmount = json.readTree(anonymous.get().uri("/admin/payments?q=1250").retrieve().body(ByteArray::class.java)).get("data").items()
        assertTrue(byAmount.isNotEmpty() && byAmount.all { it.get("amount").get("amount").asLong() == 125_000L })
        listOf("/admin/payments/export", "/admin/audit/export", "/admin/activity/export").forEach { uri ->
            val exported = json.readTree(anonymous.get().uri(uri).retrieve().body(ByteArray::class.java))
            assertEquals(exported.get("page").get("total_elements").asInt(), exported.get("data").size())
        }
        assertTrue(json.readTree(anonymous.get().uri("/admin/payments/export").retrieve().body(ByteArray::class.java)).get("data").size() > 12)
        listOf("1e100", "99999999999999999999").forEach { huge ->
            assertTrue(json.readTree(anonymous.get().uri("/admin/payments?q={q}", huge).retrieve().body(ByteArray::class.java)).get("data").isEmpty)
        }
        val failedOnEpsilon = json.readTree(anonymous.get().uri("/admin/payments?status=failed&provider=mock_epsilon").retrieve().body(ByteArray::class.java)).get("data").items()
        assertTrue(failedOnEpsilon.all { it.get("status").asString() == "failed" && it.get("provider").asString() == "mock_epsilon" })

        val ascending = json.readTree(anonymous.get().uri("/admin/payments?sort=amount&order=asc&size=50").retrieve().body(ByteArray::class.java)).get("data").items()
        val amounts = ascending.map { it.get("amount").get("amount").asLong() }
        assertEquals(amounts.sorted(), amounts)
        val newest = json.readTree(anonymous.get().uri("/admin/payments?sort=nonsense&size=50").retrieve().body(ByteArray::class.java)).get("data").items()
        val createdAt = newest.map { Instant.parse(it.get("created_at").asString()) }
        assertEquals(createdAt.sortedDescending(), createdAt)

        val requests = json.readTree(anonymous.get().uri("/admin/audit?kind=request&size=5").retrieve().body(ByteArray::class.java))
        assertTrue(requests.get("data").items().all { it.get("kind").asString() == "request" })
        assertTrue(requests.get("data").items().count() <= 5)
    }

    @Test
    fun `the admin lists the providers the gateway serves`() {
        val providers = json.readTree(anonymous.get().uri("/admin/providers").retrieve().body(ByteArray::class.java)).get("data").items()
        assertEquals(
            listOf("mock_alpha", "mock_epsilon", "mock_moncash", "mock_natcash", "mock_openfsp", "mock_wallet"),
            providers.map { it.get("id").asString() },
        )
        val epsilon = providers.single { it.get("id").asString() == "mock_epsilon" }
        assertEquals("http://127.0.0.1:8080/logos/mock_epsilon.svg", epsilon.get("logo_url").asString())
        assertEquals("MonCash (mock)", providers.single { it.get("id").asString() == "mock_moncash" }.get("display_name").asString())
    }

    @Test
    fun `a failed delivery is retried with the same body`() {
        receiver.respondWith = 500
        val id = create(body()).json().get("id").asString()
        await { recordedAttempts(id) > 0 }
        jdbc.sql("update webhook_deliveries set next_attempt_at = now() where status = 'pending'").update()
        receiver.respondWith = 204
        await { webhooksFor(id).size == 2 }
        val (first, second) = webhooksFor(id)
        assertContentEquals(first.body, second.body)
        assertEquals("1", first.headers["openfsp-delivery-attempt"])
        assertEquals("2", second.headers["openfsp-delivery-attempt"])
    }

    @Test
    fun `a string amount and a numeric description are rejected instead of coerced`() {
        val stringAmount = create(body().replace("\"amount\":125000", "\"amount\":\"125000\""))
        assertEquals(422, stringAmount.statusCode.value())
        assertEquals("invalid-field", stringAmount.problem())
        val numericDescription = create(body().replace("\"provider\":", "\"description\":42,\"provider\":"))
        assertEquals(422, numericDescription.statusCode.value())
        assertEquals("invalid-field", numericDescription.problem())
    }

    @Test
    fun `a creation that sets the fee is refused and a payment without known fees carries none`() {
        val reference = "INV-${UUID.randomUUID()}"
        val refused = create(body(reference = reference).replace("\"provider\":", "\"fee\":{\"amount\":{\"amount\":0,\"currency\":\"HTG\"},\"bearer\":\"payer\"},\"provider\":"))
        assertEquals(400, refused.statusCode.value())
        assertEquals("unknown-field", refused.problem())
        assertEquals(listOf("/fee"), refused.json().get("errors").items().map { it.get("field").asString() })
        val payment = create(body()).json()
        deliver(operator.callback(payment.get("metadata").get("gateway.order_id").asString(), "succeeded"))
        val settled = read(payment.get("id").asString())
        assertEquals("succeeded", settled.get("status").asString())
        assertTrue(!settled.has("fee"))
    }

    @Test
    fun `a fee reported with the terminal state is carried by the payment, its statement entry and the export, and a later report without it keeps it`() {
        val payment = create(body()).json()
        val id = payment.get("id").asString()
        val orderId = payment.get("metadata").get("gateway.order_id").asString()
        val fee = json.readTree("""{"amount":{"amount":2500,"currency":"HTG"},"bearer":"merchant"}""")
        operator.fees[orderId] = 2_500L to "merchant"
        assertEquals(204, deliver(operator.callback(orderId, "succeeded")))
        operator.fees.remove(orderId)
        assertEquals(204, deliver(operator.callback(orderId, "succeeded")))
        jdbc.sql("update payments set completed_at = ?, booked_at = ? where id = ?").params(OffsetDateTime.parse("2025-10-06T15:00:00Z"), OffsetDateTime.parse("2025-10-06T15:00:00Z"), id).update()
        val unknown = settledAt("2025-10-06T16:00:00Z")

        assertEquals(fee, read(id).get("fee"))
        assertEquals(fee, json.readTree(anonymous.get().uri("/admin/payments/{id}", id).retrieve().body(ByteArray::class.java)).get("payment").get("fee"))
        listOf("/v1/statements", "/admin/statements").forEach { path ->
            val entries = statement("provider=mock_alpha&business_date=2025-10-06", path).json().get("data")
            assertEquals(fee, entries.get(0).get("fee"), path)
            assertTrue(!entries.get(1).has("fee"), path)
        }
        val export = String(anonymous.get().uri("/admin/statements/export?provider=mock_alpha&business_date=2025-10-06").retrieve().body(ByteArray::class.java)!!, Charsets.UTF_8)
        assertEquals(
            listOf(
                "business_date,booked_at,provider,payment,reference,amount,currency,fee_amount,fee_bearer",
                "2025-10-06,2025-10-06T15:00:00Z,mock_alpha,$id,${payment.get("reference").asString()},1250.00,HTG,25.00,merchant",
                "2025-10-06,2025-10-06T16:00:00Z,mock_alpha,${unknown.get("id").asString()},${unknown.get("reference").asString()},1250.00,HTG,,",
                "",
            ).joinToString("\r\n"),
            export,
        )
    }

    @Test
    fun `a client callback_url answers capability not supported since no provider is declared with webhooks per_payment_url`() {
        val calls = operator.calls.get()
        val refused = create(body(provider = "mock_epsilon").replace("\"provider\":", "\"callback_url\":\"https://shop.example/events\",\"provider\":"))
        assertEquals(422, refused.statusCode.value())
        assertEquals("capability-not-supported", refused.problem())
        assertEquals(calls, operator.calls.get())
    }

    @Test
    fun `field errors name the field as sent with a JSON pointer and a stable code`() {
        val invalid = create(body(amount = 0)).json().get("errors").items()
        assertEquals(listOf("/amount/amount"), invalid.map { it.get("field").asString() })
        assertEquals(listOf("not_positive"), invalid.map { it.get("code").asString() })
        val unknown = create(body().replace("\"provider\":", "\"colour\":\"red\",\"provider\":")).json().get("errors").items()
        assertEquals(listOf("/colour"), unknown.map { it.get("field").asString() })
        assertEquals(listOf("unknown_field"), unknown.map { it.get("code").asString() })
        val mistyped = create(body().replace("\"amount\":125000", "\"amount\":\"125000\"")).json().get("errors").items().single()
        assertEquals("/amount/amount", mistyped.get("field").asString())
        assertEquals("invalid_type", mistyped.get("code").asString())
    }

    @Test
    fun `an unsatisfiable Accept is answered 406 as a problem`() {
        val id = create(body()).json().get("id").asString()
        val response = client.get().uri("/v1/payments/{id}", id).accept(MediaType.APPLICATION_XML).retrieve().toEntity(ByteArray::class.java)
        assertEquals(406, response.statusCode.value())
        assertEquals("application/problem+json", response.headers.contentType.toString())
        val problem = response.json()
        assertEquals(406, problem.get("status").asInt())
        assertEquals("not-acceptable", response.problem())
        assertTrue(problem.get("request_id").asString().startsWith("req_"))
    }

    @Test
    fun `reading a terminal payment is never rate limited`() {
        val payment = create(body()).json()
        val id = payment.get("id").asString()
        deliver(operator.callback(payment.get("metadata").get("gateway.order_id").asString(), "succeeded"))
        val statuses = (1..6).map { client.get().uri("/v1/payments/{id}", id).retrieve().toEntity(ByteArray::class.java).statusCode.value() }
        assertEquals(List(6) { 200 }, statuses)
    }

    @Test
    fun `a payment is found by its reference in any normalization form`() {
        val prefix = "INV-${UUID.randomUUID()}-"
        val id = create(body(reference = "${prefix}K")).json().get("id").asString()
        val found = json.readTree(client.get().uri("/v1/payments?reference={reference}", "${prefix}K").retrieve().body(ByteArray::class.java)).get("data").items()
        assertEquals(listOf(id), found.map { it.get("id").asString() })
    }

    @Test
    fun `deliveries to a disabled endpoint are neither claimed nor sent until it is enabled again`() {
        receiver.respondWith = 500
        val id = create(body()).json().get("id").asString()
        await { recordedAttempts(id) > 0 }
        val eventId = jdbc.sql("select id from events where payment_id = ?").param(id).query(String::class.java).single()
        jdbc
            .sql("insert into webhook_deliveries (event_id, endpoint, url, status, attempts, next_attempt_at, updated_at) values (?, 'paused-shop', ?, 'pending', 0, now(), now())")
            .param(eventId)
            .param(receiver.url)
            .update()
        jdbc
            .sql("insert into webhook_endpoint_state (endpoint, disabled_at, reason) values ('demo-shop', now(), 'test') on conflict (endpoint) do update set disabled_at = now()")
            .update()
        val attempts = { endpoint: String ->
            jdbc
                .sql("select count(*) from webhook_attempts a join webhook_deliveries d on d.id = a.delivery_id where d.event_id = ? and d.endpoint = ?")
                .param(eventId)
                .param(endpoint)
                .query(Int::class.java)
                .single()
        }
        try {
            receiver.respondWith = 204
            jdbc.sql("update webhook_deliveries set next_attempt_at = now() where status = 'pending' and event_id = ?").param(eventId).update()
            Thread.sleep(DISABLED_OBSERVATION_MILLIS)
            assertEquals(1, webhooksFor(id).size)
            assertEquals(1, attempts("demo-shop"))
            assertEquals(0, attempts("paused-shop"))
        } finally {
            jdbc.sql("update webhook_endpoint_state set disabled_at = null, reason = null where endpoint = 'demo-shop'").update()
            jdbc.sql("delete from webhook_attempts where delivery_id in (select id from webhook_deliveries where endpoint = 'paused-shop')").update()
            jdbc.sql("delete from webhook_deliveries where endpoint = 'paused-shop'").update()
        }
        await { webhooksFor(id).size == 2 }
    }

    companion object {
        private const val DISABLED_OBSERVATION_MILLIS = 600L
        private val operator =
            FakeOpenFspOperator(
                mapOf(
                    "mock_alpha" to FakeProfile(listOf("payments", "payments.lookup", "payments.fee", "payments.statement", "webhooks.emit"), "payer_approval", payerRequired = true),
                    "mock_epsilon" to FakeProfile(listOf("payments", "webhooks.per_payment_url"), "redirect", returnUrlRequired = true),
                ),
            )
        private val receiver = WebhookReceiver()
        private val ED25519_SPKI_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)

        @JvmStatic
        @DynamicPropertySource
        fun operatorProperties(registry: DynamicPropertyRegistry) {
            registry.add("openfsp.providers.mock_alpha.base-url") { operator.url("mock_alpha") }
            registry.add("openfsp.providers.mock_epsilon.base-url") { operator.url("mock_epsilon") }
            registry.add("openfsp.provider-timeout") { "2s" }
            registry.add("openfsp.webhooks.poll-interval") { "100ms" }
            registry.add("openfsp.reconciliation-interval") { "1h" }
            registry.add("openfsp.providers.mock_alpha.limits.min-amount") { "1000" }
            registry.add("openfsp.providers.mock_alpha.limits.max-amount") { "7500000" }
            registry.add("openfsp.providers.mock_alpha.limits.amount-step") { "100" }
            registry.add("openfsp.webhooks.endpoints.demo-shop.url") { receiver.url }
            registry.add("openfsp.webhooks.endpoints.paused-shop.url") { receiver.url }
            registry.add("openfsp.webhooks.endpoints.paused-shop.events") { "payment.created" }
            registry.add("openfsp.webhooks.endpoints.paused-shop.enabled") { "false" }
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            operator.close()
            receiver.close()
        }
    }
}
