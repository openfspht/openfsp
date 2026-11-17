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
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import org.openfsp.gateway.auth.api.ManageKeys
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.payment.api.ReconcilePendingPayments
import org.openfsp.gateway.statement.api.ReconcileStatements
import org.openfsp.protocol.OrderIdDerivation
import org.openfsp.protocol.ResourceIds
import java.net.URI
import java.time.Instant
import org.openfsp.protocol.webhook.InMemoryNonceStore
import org.openfsp.protocol.webhook.NonceStore
import org.openfsp.gateway.webhooks.WebhookNoncePurgeJob
import org.openfsp.protocol.webhook.Verification
import org.openfsp.protocol.webhook.WebhookVerifier
import org.openfsp.gateway.webhook.api.RotateSigningKeys
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("development", "test")
class GatewayApplicationTests {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var json: JsonMapper

    @Autowired
    private lateinit var jdbc: JdbcClient

    @Autowired
    private lateinit var keys: ManageKeys

    @Autowired
    private lateinit var payments: ReconcilePendingPayments

    @Autowired
    private lateinit var calendar: BusinessCalendar

    @Autowired
    private lateinit var rotation: RotateSigningKeys

    @Autowired
    private lateinit var statements: ReconcileStatements

    @Autowired
    private lateinit var nonces: NonceStore

    @Autowired
    private lateinit var noncePurge: WebhookNoncePurgeJob

    @Autowired
    private lateinit var orderIds: OrderIdDerivation

    private val key by lazy { keys.issue("tests", setOf(Scope.PAYMENTS_READ, Scope.PAYMENTS_WRITE)).secret }

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
        jdbc.sql("delete from statement_entries").update()
        jdbc.sql("delete from statements").update()
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
    fun `a replayed problem carries the Request-Id of its recorded body`() {
        val body = body()
        create(body)
        val key = UUID.randomUUID().toString()
        val conflict = create(body, key)
        val replay = create(body, key)
        assertEquals("true", replay.headers.getFirst("Idempotent-Replay"))
        assertContentEquals(conflict.body, replay.body)
        assertEquals(conflict.headers.getFirst("Request-Id"), conflict.json().get("request_id").asString())
        assertEquals(replay.json().get("request_id").asString(), replay.headers.getFirst("Request-Id"))
    }

    @Test
    fun `the gateway publishes its upstream data as metadata keys that never replace a client key`() {
        val reference = "INV-${UUID.randomUUID()}"
        val created = create(body(reference = reference).dropLast(1) + ""","metadata":{"order":"184"}}""").json()
        assertTrue(created.get("provider_order_id") == null && created.get("provider_reference") == null)
        val metadata = created.get("metadata")
        assertEquals("184", metadata.get("order").asString())
        assertEquals(orderIds.derive(reference), metadata.get("gateway.order_id").asString())
        assertEquals(operator.paymentIdOf(orderIds.derive(reference)), metadata.get("gateway.provider_reference").asString())

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
    fun `synchronizing against an unknown operator status or another amount answers 200 with the payment unchanged`() {
        listOf("in_review" to null, "succeeded" to 124_900L).forEach { (status, amount) ->
            val payment = create(body()).json()
            val id = payment.get("id").asString()
            val before = read(id)
            operator.statuses[payment.get("metadata").get("gateway.order_id").asString()] = status
            amount?.let { operator.amounts[payment.get("metadata").get("gateway.order_id").asString()] = it }
            val synchronized = client.post().uri("/v1/payments/{id}/synchronize", id).retrieve().toEntity(ByteArray::class.java)
            assertEquals(200, synchronized.statusCode.value(), status)
            assertEquals(before, synchronized.json(), status)
        }
    }

    @Test
    fun `synchronizing a terminal payment does not call the operator`() {
        val payment = create(body()).json()
        val id = payment.get("id").asString()
        val orderId = payment.get("metadata").get("gateway.order_id").asString()
        operator.statuses[orderId] = "failed"
        val synchronized = json.readTree(client.post().uri("/v1/payments/{id}/synchronize", id).retrieve().body(ByteArray::class.java))
        assertEquals("failed", synchronized.get("status").asString())
        assertEquals("unspecified", synchronized.get("failure_reason").asString())

        val calls = operator.calls.get()
        client.post().uri("/v1/payments/{id}/synchronize", id).retrieve().toBodilessEntity()
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
        val revoked = keys.issue("tests", setOf(Scope.PAYMENTS_READ))
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
        val limited = keys.issue("tests", setOf(Scope.CAPABILITIES_READ)).secret
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
            listOf("payments", "payments.lookup", "payments.statement", "webhooks.emit"),
            configuration.get("capabilities_supported").items().map { it.asString() },
        )
        assertEquals("OKP", configuration.get("webhook_keys").items().first().get("kty").asString())
        assertTrue(!raw.contains("mock_alpha") && !raw.contains("/admin") && !raw.contains("gateway_statements"))
    }

    @Test
    fun `capabilities are announced per provider to every key`() {
        val reader = keys.issue("tests", setOf(Scope.CAPABILITIES_READ)).secret
        val response = anonymous.get().uri("/v1/capabilities").header("Authorization", "Bearer $reader").retrieve().toEntity(ByteArray::class.java)
        val provider = response.json().get("providers").get(0)
        assertEquals("mock_alpha", provider.get("id").asString())
        assertEquals(
            listOf("payments", "payments.lookup", "webhooks.emit"),
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
    fun `a payment still pending after its expiry is expired at the next lookup`() {
        operator.expiresIn = 0
        val id = create(body()).json().get("id").asString()
        Thread.sleep(5)
        val synchronized = json.readTree(client.post().uri("/v1/payments/{id}/synchronize", id).retrieve().body(ByteArray::class.java))
        assertEquals("expired", synchronized.get("status").asString())
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

    @Test
    fun `a statement settles, flags unmatched lines, lists missing payments and expires the rest`() {
        val reader = keys.issue("tests", setOf(Scope.STATEMENTS_READ)).secret
        val settledByStatement = create(body(provider = "mock_epsilon")).json()
        operator.expiresIn = 0
        val expiring = create(body(provider = "mock_epsilon")).json()
        operator.expiresIn = 900
        val missing = create(body(provider = "mock_epsilon")).json()
        val missingOrder = missing.get("metadata").get("gateway.order_id").asString()
        val missingCallback = operator.callback(missingOrder, "succeeded", operator = "mock_epsilon")
        anonymous
            .post()
            .uri(URI(operator.callbackUrls.getValue(missingOrder)).path)
            .contentType(MediaType.APPLICATION_JSON)
            .headers { h -> missingCallback.headers.forEach(h::set) }
            .body(missingCallback.body)
            .retrieve()
            .toBodilessEntity()

        val day = calendar.today()
        operator.statementLine(operator.paymentIdOf(settledByStatement.get("metadata").get("gateway.order_id").asString()), 125_000, "${day}T10:15:00")
        operator.statementLine(null, 7_500, "${day}T11:00:00")
        operator.statementClosed = true
        Thread.sleep(5)
        statements.fetch("mock_epsilon", day)

        assertEquals("succeeded", read(settledByStatement.get("id").asString()).get("status").asString())
        assertEquals("expired", read(expiring.get("id").asString()).get("status").asString())
        val statement =
            json.readTree(
                anonymous.get().uri("/v1/statements?provider=mock_epsilon&business_date={day}", day).header("Authorization", "Bearer $reader").retrieve().body(ByteArray::class.java),
            ).get("data").get(0)
        assertTrue(statement.get("complete").asBoolean())
        assertEquals(listOf("matched", "unmatched"), statement.get("entries").items().map { it.get("match").asString() })
        assertTrue(missing.get("id").asString() in statement.get("missing_payments").items().map { it.asString() })
        val unmatched =
            json.readTree(
                anonymous.get().uri("/v1/statement_entries?match=unmatched&provider=mock_epsilon").header("Authorization", "Bearer $reader").retrieve().body(ByteArray::class.java),
            )
        val entry = unmatched.get("data").items().single { it.get("amount").get("amount").asLong() == 7_500L }
        assertEquals(setOf("type", "amount", "booked_at", "match"), entry.propertyNames().toSet())
        assertEquals("forbidden", client.get().uri("/v1/statements?provider=mock_epsilon&business_date={day}", day).retrieve().toEntity(ByteArray::class.java).problem())
    }

    @Test
    fun `concurrent fetches of one statement record each line once`() {
        val day = calendar.today()
        (1..3).forEach { operator.statementLine(null, 1_000L * it, "${day}T09:0$it:00") }
        val start = CyclicBarrier(CONCURRENT_FETCHES)
        val pool = Executors.newFixedThreadPool(CONCURRENT_FETCHES)
        try {
            (1..CONCURRENT_FETCHES).map { pool.submit { start.await(); statements.fetch("mock_epsilon", day) } }.forEach { it.get() }
        } finally {
            pool.shutdown()
        }

        val recorded = jdbc.sql("select count(*) from statement_entries e join statements s on s.id = e.statement_id where s.provider = 'mock_epsilon' and s.business_date = ?").param(day).query(Int::class.java).single()
        assertEquals(3, recorded)
    }

    @Test
    fun `a closed statement expires only the payments of its day whose expiry has passed`() {
        val day = calendar.today()
        operator.expiresIn = 600
        val notYetExpired = create(body(provider = "mock_epsilon")).json().get("id").asString()
        operator.expiresIn = -2 * SECONDS_PER_DAY
        val expiredOnAnotherDay = create(body(provider = "mock_epsilon")).json().get("id").asString()
        operator.expiresIn = 900
        operator.statementClosed = true
        statements.fetch("mock_epsilon", day)

        assertEquals("pending", read(notYetExpired).get("status").asString())
        assertEquals("pending", read(expiredOnAnotherDay).get("status").asString())
    }

    @Test
    fun `the gateway statement lists the payments settled during the business day`() {
        val reader = keys.issue("tests", setOf(Scope.STATEMENTS_READ)).secret
        val settled = create(body(amount = 50_000)).json()
        deliver(operator.callback(settled.get("metadata").get("gateway.order_id").asString(), "succeeded", amount = 50_000))
        val declined = create(body()).json()
        deliver(operator.callback(declined.get("metadata").get("gateway.order_id").asString(), "failed", reason = "declined"))
        val pending = create(body()).json().get("id").asString()
        val day = calendar.today()
        val read = { date: Any ->
            json.readTree(
                anonymous
                    .get()
                    .uri("/gateway/statements?provider=mock_alpha&business_date={day}", date)
                    .header("Authorization", "Bearer $reader")
                    .retrieve()
                    .body(ByteArray::class.java),
            ).get("data").get(0)
        }
        val today = read(day)
        val listed = today.get("payments").items().map { it.get("payment").asString() }
        assertTrue(settled.get("id").asString() in listed)
        assertTrue(pending !in listed)
        val failed = today.get("payments").items().single { it.get("payment").asString() == declined.get("id").asString() }
        assertEquals("failed", failed.get("status").asString())
        assertEquals(setOf("payment", "reference", "status", "amount", "completed_at"), failed.propertyNames().toSet())
        assertTrue(!today.get("final").asBoolean())
        assertTrue(today.get("totals").get("succeeded").get("count").asInt() >= 1)
        val past = read(day.minusYears(1))
        assertTrue(past.get("final").asBoolean())
        assertEquals(0, past.get("payments").size())
        assertEquals("forbidden", client.get().uri("/gateway/statements?provider=mock_alpha&business_date={day}", day).retrieve().toEntity(ByteArray::class.java).problem())
        assertEquals("unauthenticated", anonymous.get().uri("/gateway/statements?provider=mock_alpha&business_date={day}", day).retrieve().toEntity(ByteArray::class.java).problem())
        assertEquals(404, anonymous.get().uri("/v1/gateway_statements?provider=mock_alpha&business_date={day}", day).header("Authorization", "Bearer $reader").retrieve().toBodilessEntity().statusCode.value())
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
                "insert into payments (id, reference, status, amount, currency, provider, provider_order_id, created_at, updated_at) " +
                    "values (?, ?, 'pending', 125000, 'HTG', 'mock_epsilon', ?, now(), now())",
            ).params(id, reference, UUID.randomUUID().toString())
            .update()

        val found = json.readTree(client.get().uri("/v1/payments?reference={reference}", reference).retrieve().body(ByteArray::class.java))
        assertEquals(0, found.get("data").size())
        assertEquals(404, client.get().uri("/v1/payments/{id}", id).retrieve().toBodilessEntity().statusCode.value())
        assertEquals(404, client.post().uri("/v1/payments/{id}/synchronize", id).retrieve().toBodilessEntity().statusCode.value())
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
    fun `synchronizing one payment too often is rate limited with Retry-After`() {
        val id = create(body()).json().get("id").asString()
        val statuses = (1..4).map { client.post().uri("/v1/payments/{id}/synchronize", id).retrieve().toEntity(ByteArray::class.java) }
        assertEquals(listOf(200, 200, 200), statuses.take(3).map { it.statusCode.value() })
        assertEquals("rate-limited", statuses[3].problem())
        assertTrue(statuses[3].headers.getFirst("Retry-After")!!.toInt() >= 1)
    }

    @Test
    fun `the admin issues a key shown once and revokes it at once`() {
        val issued =
            json.readTree(
                anonymous.post().uri("/admin/keys").contentType(MediaType.APPLICATION_JSON)
                    .body("""{"principal":"shop","scopes":["payments:read"]}""").retrieve().body(ByteArray::class.java),
            )
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
                    .body("""{"principal":"lapsed","scopes":["payments:read"],"expires_at":"2001-01-01T00:00:00Z"}""").retrieve().body(ByteArray::class.java),
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
        assertEquals(201, add("""{"name":"erp","url":"http://127.0.0.1:9/erp","events":["payment.canceled"]}""").statusCode.value())
        assertEquals(201, add("""{"name":"accounting","url":"http://127.0.0.1:9/books","events":["payment.canceled","payment.expired"]}""").statusCode.value())
        assertEquals("invalid-field", add("""{"name":"erp","url":"http://127.0.0.1:9/again","events":["payment.created"]}""").problem())
        val admin = json.readTree(anonymous.get().uri("/admin/activity?kind=admin&size=50").retrieve().body(ByteArray::class.java)).get("data").items()
        assertTrue(admin.any { it.get("source").asString() == "webhook_added" && it.get("subject").asString() == "accounting" })
        assertEquals("invalid-field", add("""{"name":"bad","url":"http://127.0.0.1:9/x","events":["payment.unknown"]}""").problem())
        assertEquals("invalid-field", add("""{"name":"Bad Name","url":"http://127.0.0.1:9/x","events":["payment.created"]}""").problem())

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
        val createdAt = newest.map { it.get("created_at").asString() }
        assertEquals(createdAt.sortedDescending(), createdAt)

        val requests = json.readTree(anonymous.get().uri("/admin/audit?kind=request&size=5").retrieve().body(ByteArray::class.java))
        assertTrue(requests.get("data").items().all { it.get("kind").asString() == "request" })
        assertTrue(requests.get("data").items().count() <= 5)
    }

    @Test
    fun `the admin lists the providers the gateway serves`() {
        val providers = json.readTree(anonymous.get().uri("/admin/providers").retrieve().body(ByteArray::class.java)).get("data").items()
        assertEquals(
            listOf("mock_alpha", "mock_epsilon", "mock_moncash", "mock_natcash", "mock_openfsp"),
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
    fun `a client callback_url is refused as an unknown field since no provider announces webhooks per_payment_url`() {
        val calls = operator.calls.get()
        val refused = create(body(provider = "mock_epsilon").replace("\"provider\":", "\"callback_url\":\"https://shop.example/events\",\"provider\":"))
        assertEquals(400, refused.statusCode.value())
        assertEquals("unknown-field", refused.problem())
        assertEquals(listOf("/callback_url"), refused.json().get("errors").items().map { it.get("field").asString() })
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
    fun `synchronizing a terminal payment is never rate limited`() {
        val payment = create(body()).json()
        val id = payment.get("id").asString()
        deliver(operator.callback(payment.get("metadata").get("gateway.order_id").asString(), "succeeded"))
        val statuses = (1..6).map { client.post().uri("/v1/payments/{id}/synchronize", id).retrieve().toEntity(ByteArray::class.java).statusCode.value() }
        assertEquals(List(6) { 200 }, statuses)
    }

    @Test
    fun `the statements of a provider without the statement capability are empty collections`() {
        val reader = keys.issue("tests", setOf(Scope.STATEMENTS_READ)).secret
        val read = { uri: String ->
            anonymous.get().uri(uri).header("Authorization", "Bearer $reader").retrieve().toEntity(ByteArray::class.java)
        }
        listOf("/v1/statements?provider=mock_alpha&business_date=${calendar.today()}", "/v1/statement_entries?match=unmatched&provider=mock_alpha").forEach { uri ->
            val response = read(uri)
            assertEquals(200, response.statusCode.value(), uri)
            assertEquals(json.readTree("""{"data":[]}"""), response.json(), uri)
        }
        assertEquals("invalid-field", read("/v1/statements?provider=mock_alpha&business_date=tomorrow").problem())
        assertEquals("invalid-field", read("/v1/statement_entries?match=matched&provider=mock_alpha").problem())
        val unknown = read("/v1/statements?provider=unknown_provider&business_date=${calendar.today()}")
        assertEquals(422, unknown.statusCode.value())
        assertEquals("capability-not-supported", unknown.problem())
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
        private const val CONCURRENT_FETCHES = 8
        private val operator =
            FakeOpenFspOperator(
                mapOf(
                    "mock_alpha" to FakeProfile(listOf("payments", "payments.lookup", "webhooks.emit"), "payer_approval", payerRequired = true),
                    "mock_epsilon" to FakeProfile(listOf("payments", "payments.statement", "webhooks.per_payment_url"), "redirect", returnUrlRequired = true),
                ),
            )
        private val receiver = WebhookReceiver()
        private const val SECONDS_PER_DAY = 86_400L
        private val ED25519_SPKI_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)

        @JvmStatic
        @DynamicPropertySource
        fun operatorProperties(registry: DynamicPropertyRegistry) {
            registry.add("openfsp.providers.mock_alpha.base-url") { operator.url("mock_alpha") }
            registry.add("openfsp.providers.mock_epsilon.base-url") { operator.url("mock_epsilon") }
            registry.add("openfsp.statement-interval") { "1h" }
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
