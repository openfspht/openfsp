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
import java.net.URI
import java.time.Instant
import org.openfsp.protocol.webhook.InMemoryNonceStore
import org.openfsp.protocol.webhook.Verification
import org.openfsp.protocol.webhook.WebhookVerifier
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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
    private lateinit var statements: ReconcileStatements

    private val key by lazy { keys.issue("tests", setOf(Scope.PAYMENTS_READ, Scope.PAYMENTS_WRITE)).secret }

    private val anonymous by lazy {
        RestClient
            .builder()
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
        operator.failWith = null
        operator.expiresIn = 900
        operator.errorMessage = "down"
        operator.statementLines.clear()
        operator.statementClosed = false
        receiver.respondWith = 204
    }

    private fun verifier(): WebhookVerifier {
        val jwks = json.readTree(anonymous.get().uri("/v1/webhooks/keys").retrieve().body(ByteArray::class.java)).get("keys")
        val keys =
            jwks.associate { jwk ->
                val raw = Base64.getUrlDecoder().decode(jwk.get("x").asString())
                val spki = ED25519_SPKI_PREFIX + raw
                jwk.get("kid").asString() to KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(spki))
            }
        return WebhookVerifier(keys = { keys[it] }, nonces = InMemoryNonceStore())
    }

    private fun webhooksFor(id: String) = receiver.received.filter { json.readTree(it.body).get("resource_id").asString() == id }

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
        callback: Pair<String, String>,
        signature: String = callback.second,
    ) = client
        .post()
        .uri("/callbacks/mock_alpha")
        .headers { it.remove("Authorization") }
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Mock-Signature", signature)
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
    fun `credentials echoed by the operator never reach the client`() {
        operator.failWith = 400
        operator.errorMessage = "Rejected request authorized with Bearer mock_alpha_test_key"
        val failed = create(body())
        assertEquals("provider-error", failed.problem())
        val message = failed.json().get("provider_detail").get("message").asString()
        assertTrue(!message.contains("mock_alpha_test_key"), message)
        assertTrue(message.contains("[redacted]"))
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
        assertEquals("http://127.0.0.1:8080/v1/webhooks/keys", configuration.get("webhook_keys_uri").asString())
        assertEquals(listOf("ed25519"), configuration.get("webhook_signature_algorithms_supported").items().map { it.asString() })
        assertEquals(
            listOf("payments", "payments.lookup", "payments.statement", "webhooks.emit", "webhooks.per_payment_url", "webhooks.verify"),
            configuration.get("capabilities_supported").items().map { it.asString() },
        )
        assertTrue(!raw.contains("mock_alpha") && !raw.contains("/admin"))
    }

    @Test
    fun `capabilities are announced per provider to every key`() {
        val reader = keys.issue("tests", setOf(Scope.CAPABILITIES_READ)).secret
        val response = anonymous.get().uri("/v1/capabilities").header("Authorization", "Bearer $reader").retrieve().toEntity(ByteArray::class.java)
        val provider = response.json().get("providers").get(0)
        assertEquals("mock_alpha", provider.get("id").asString())
        assertEquals(
            listOf("payments", "payments.lookup", "webhooks.emit", "webhooks.verify"),
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
        operator.statuses[payment.get("provider_order_id").asString()] = "SUCCESSFUL"
        jdbc.sql("update payments set updated_at = now() - interval '1 minute' where id = ?").param(id).update()
        payments.reconcile(500)
        assertEquals("succeeded", read(id).get("status").asString())
    }

    @Test
    fun `a callback on the payment URL settles it once and a wrong token has no effect`() {
        val payment = create(body(provider = "mock_epsilon")).json()
        val orderId = payment.get("provider_order_id").asString()
        val callbackUrl = URI(operator.callbackUrls.getValue(orderId))
        assertTrue(Regex("^/callbacks/mock_epsilon/[A-Za-z0-9_-]{22}$").matches(callbackUrl.path), callbackUrl.path)
        val unsigned = """{"payment_id":"ME-1","order_id":"$orderId","status":"SUCCESSFUL","amount":"1250.00"}"""
        val post = { path: String -> anonymous.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(unsigned).retrieve().toBodilessEntity().statusCode.value() }

        assertEquals(404, post("/callbacks/mock_epsilon/" + "A".repeat(22)))
        assertEquals("pending", read(payment.get("id").asString()).get("status").asString())
        assertEquals(204, post(callbackUrl.path))
        assertEquals("succeeded", read(payment.get("id").asString()).get("status").asString())
        assertEquals(404, post(callbackUrl.path))
    }

    @Test
    fun `a statement settles, flags unmatched lines, lists missing payments and expires the rest`() {
        val reader = keys.issue("tests", setOf(Scope.STATEMENTS_READ)).secret
        val settledByStatement = create(body(provider = "mock_epsilon")).json()
        operator.expiresIn = 0
        val expiring = create(body(provider = "mock_epsilon")).json()
        operator.expiresIn = 900
        val missing = create(body(provider = "mock_epsilon")).json()
        val missingOrder = missing.get("provider_order_id").asString()
        anonymous
            .post()
            .uri(URI(operator.callbackUrls.getValue(missingOrder)).path)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"payment_id":"ME-3","order_id":"$missingOrder","status":"SUCCESSFUL","amount":"1250.00"}""")
            .retrieve()
            .toBodilessEntity()

        val day = calendar.today()
        operator.statementLine("ME-1", settledByStatement.get("provider_order_id").asString(), "1250.00", "${day}T10:15:00")
        operator.statementLine("ME-UNKNOWN", null, "75.00", "${day}T11:00:00")
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
        assertTrue(unmatched.get("data").items().any { it.get("provider_reference").asString() == "ME-UNKNOWN" })
        assertEquals("forbidden", client.get().uri("/v1/statements?provider=mock_epsilon&business_date={day}", day).retrieve().toEntity(ByteArray::class.java).problem())
    }

    @Test
    fun `the gateway statement lists the payments settled during the business day`() {
        val reader = keys.issue("tests", setOf(Scope.STATEMENTS_READ)).secret
        val settled = create(body(amount = 50_000)).json()
        deliver(operator.callback(settled.get("provider_order_id").asString(), "SUCCESSFUL", amount = "500.00"))
        val pending = create(body()).json().get("id").asString()
        val day = calendar.today()
        val read = { date: Any ->
            json.readTree(
                anonymous
                    .get()
                    .uri("/v1/gateway_statements?provider=mock_alpha&business_date={day}", date)
                    .header("Authorization", "Bearer $reader")
                    .retrieve()
                    .body(ByteArray::class.java),
            ).get("data").get(0)
        }
        val today = read(day)
        val listed = today.get("payments").items().map { it.get("payment").asString() }
        assertTrue(settled.get("id").asString() in listed)
        assertTrue(pending !in listed)
        assertTrue(!today.get("final").asBoolean())
        assertTrue(today.get("totals").get("succeeded").get("count").asInt() >= 1)
        val past = read(day.minusYears(1))
        assertTrue(past.get("final").asBoolean())
        assertEquals(0, past.get("payments").size())
        assertEquals("forbidden", client.get().uri("/v1/gateway_statements?provider=mock_alpha&business_date={day}", day).retrieve().toEntity(ByteArray::class.java).problem())
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
                .uri("/return/mock_epsilon?order_id={id}&return_url=https://attacker.example", payment.get("provider_order_id").asString())
                .retrieve()
                .toBodilessEntity()
        assertEquals(303, back.statusCode.value())
        assertEquals("https://shop.example/orders/$reference", back.headers.location.toString())
        assertEquals(404, noRedirects.get().uri("/return/mock_epsilon?order_id=unknown").retrieve().toBodilessEntity().statusCode.value())
        assertEquals(404, noRedirects.get().uri("/return/mock_epsilon").retrieve().toBodilessEntity().statusCode.value())
    }

    @Test
    fun `an unknown outcome at a provider without idempotency keeps the payment pending and is never resent`() {
        val key = UUID.randomUUID().toString()
        val reference = "INV-${UUID.randomUUID()}"
        val body = body(reference = reference, provider = "mock_beta")
        operator.failWith = 500
        val first = create(body, key)
        assertEquals("provider-error", first.problem())
        assertEquals("unknown", first.json().get("effect").asString())
        operator.failWith = null

        val calls = operator.calls.get()
        assertEquals("reference-conflict", create(body, key).problem())
        assertEquals(calls, operator.calls.get())
        val payment =
            json.readTree(client.get().uri("/v1/payments?reference={reference}", reference).retrieve().body(ByteArray::class.java)).get("data").get(0)
        assertEquals("pending", payment.get("status").asString())
        assertEquals("payer_approval", payment.get("next_action").get("type").asString())
        val detail = json.readTree(anonymous.get().uri("/admin/payments/{id}", payment.get("id").asString()).retrieve().body(ByteArray::class.java))
        assertEquals(1, detail.get("indeterminate").size())
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
    }

    @Test
    fun `every transition is delivered as a signed webhook in sequence`() {
        val payment = create(body()).json()
        val id = payment.get("id").asString()
        deliver(operator.callback(payment.get("provider_order_id").asString(), "SUCCESSFUL"))
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
        deliver(operator.callback(payment.get("provider_order_id").asString(), "SUCCESSFUL"))
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
    fun `an attestation settles a payment once and a later contradiction is a conflict`() {
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
        json.readTree(attest("failed"))
        assertEquals("succeeded", read(id).get("status").asString())
        assertEquals(1, conflicts(id))
    }

    @Test
    fun `an immediate rotation publishes and signs with the new key only`() {
        val rotated =
            json.readTree(anonymous.post().uri("/admin/webhooks/keys/rotate?immediate=true").retrieve().body(ByteArray::class.java)).get("kid").asString()
        val published = json.readTree(anonymous.get().uri("/v1/webhooks/keys").retrieve().body(ByteArray::class.java)).get("keys").items()
        assertEquals(listOf(rotated), published.map { it.get("kid").asString() })

        val id = create(body()).json().get("id").asString()
        await { webhooksFor(id).isNotEmpty() }
        val webhook = webhooksFor(id).first()
        assertTrue(webhook.headers.getValue("signature-input").contains("keyid=\"$rotated\""))
        assertIs<Verification.Verified>(verifier().verify("POST", receiver.url, { webhook.headers[it.lowercase()] }, webhook.body))
        val endpoints = json.readTree(anonymous.get().uri("/admin/webhooks/endpoints").retrieve().body(ByteArray::class.java)).get("data").items()
        assertEquals("demo-shop", endpoints.single().get("name").asString())
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
        val activity = json.readTree(anonymous.get().uri("/admin/activity?kind=transition&limit=50").retrieve().body(ByteArray::class.java)).get("data").items()
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
    fun `the admin lists the providers the gateway serves`() {
        val providers = json.readTree(anonymous.get().uri("/admin/providers").retrieve().body(ByteArray::class.java)).get("data").items()
        assertEquals(
            listOf("mock_alpha", "mock_beta", "mock_epsilon", "mock_moncash", "mock_natcash", "mock_openfsp"),
            providers.map { it.get("id").asString() },
        )
        val epsilon = providers.single { it.get("id").asString() == "mock_epsilon" }
        assertEquals("http://127.0.0.1:8080/logos/mock_epsilon.svg", epsilon.get("logo_url").asString())
        assertEquals("MonCash", providers.single { it.get("id").asString() == "mock_moncash" }.get("display_name").asString())
    }

    @Test
    fun `a failed delivery is retried with the same body`() {
        receiver.respondWith = 500
        val id = create(body()).json().get("id").asString()
        await { webhooksFor(id).isNotEmpty() }
        jdbc.sql("update webhook_deliveries set next_attempt_at = now() where status = 'pending'").update()
        receiver.respondWith = 204
        await { webhooksFor(id).size == 2 }
        val (first, second) = webhooksFor(id)
        assertContentEquals(first.body, second.body)
        assertEquals("1", first.headers["openfsp-delivery-attempt"])
        assertEquals("2", second.headers["openfsp-delivery-attempt"])
    }

    companion object {
        private val operator = FakeAlphaOperator()
        private val receiver = WebhookReceiver()
        private val ED25519_SPKI_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)

        @JvmStatic
        @DynamicPropertySource
        fun operatorProperties(registry: DynamicPropertyRegistry) {
            registry.add("openfsp.providers.mock_alpha.base-url") { operator.baseUrl }
            registry.add("openfsp.providers.mock_epsilon.base-url") { operator.url("mock_epsilon") }
            registry.add("openfsp.providers.mock_beta.base-url") { operator.url("mock_beta") }
            registry.add("openfsp.statement-interval") { "1h" }
            registry.add("openfsp.provider-timeout") { "2s" }
            registry.add("openfsp.webhooks.poll-interval") { "100ms" }
            registry.add("openfsp.reconciliation-interval") { "1h" }
            registry.add("openfsp.providers.mock_alpha.limits.min-amount") { "1000" }
            registry.add("openfsp.providers.mock_alpha.limits.max-amount") { "7500000" }
            registry.add("openfsp.providers.mock_alpha.limits.amount-step") { "100" }
            registry.add("openfsp.webhooks.endpoints.demo-shop.url") { receiver.url }
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            operator.close()
            receiver.close()
        }
    }
}
