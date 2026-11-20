package org.openfsp.simulator

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openfsp.protocol.webhook.InMemoryNonceStore
import org.openfsp.protocol.webhook.Verification
import org.openfsp.protocol.webhook.WebhookJwk
import org.openfsp.protocol.webhook.WebhookVerifier
import org.openfsp.simulator.control.ControlScope
import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.control.api.ControlSimulation
import org.openfsp.simulator.scenario.Scenario
import org.openfsp.simulator.operator.Accounts
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.util.Base64
import java.util.HexFormat
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

data class ReceivedCallback(
    val headers: HttpHeaders,
    val body: ByteArray,
)

@RestController
class CallbackRecorder {
    val received = CopyOnWriteArrayList<ReceivedCallback>()

    val natCash = CopyOnWriteArrayList<Map<String, Any?>>()

    @PostMapping("/test/natcash")
    fun receiveNatCash(
        @RequestBody body: Map<String, Any?>,
    ) {
        natCash += body
    }

    @PostMapping("/test/callbacks")
    fun receive(
        @RequestHeader headers: HttpHeaders,
        @RequestBody body: ByteArray,
    ) {
        received += ReceivedCallback(headers, body)
    }
}

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
@ActiveProfiles("development", "conformance")
class SimulatorApplicationTests {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var state: ControlSimulation

    @Autowired
    private lateinit var control: ControlSimulation

    @Autowired
    private lateinit var clock: SimulationClock

    @Autowired
    private lateinit var recorder: CallbackRecorder

    @Autowired
    private lateinit var json: JsonMapper

    private val client by lazy { localClient(port) }

    private val callbackUrl get() = "http://127.0.0.1:$port/test/callbacks"

    private fun sha256(value: String) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))

    private fun hmac(
        key: String,
        value: String,
    ) = HexFormat.of().formatHex(Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.toByteArray(), "HmacSHA256")) }.doFinal(value.toByteArray()))

    @BeforeEach
    fun reset() {
        control.reset(ControlScope.Instance)
        recorder.received.clear()
        recorder.natCash.clear()
    }

    private fun request(
        reference: String,
        payer: Boolean = true,
    ): Map<String, Any> =
        mapOf(
            "reference" to reference,
            "amount" to mapOf("amount" to 125000, "currency" to "HTG"),
            "provider" to "mock_openfsp",
            "callback_url" to callbackUrl,
            "locale" to "ht",
        ) + (if (payer) mapOf("payer" to mapOf("phone_number" to "+50934567890")) else emptyMap())

    private fun create(
        reference: String,
        key: String = API_KEY,
        idempotencyKey: String? = "key-$reference",
        body: Any = request(reference),
    ): ResponseEntity<Map<*, *>> =
        client
            .post()
            .uri("/mock_openfsp/v1/payments")
            .header("Authorization", "Bearer $key")
            .headers { if (idempotencyKey != null) it.set("Idempotency-Key", idempotencyKey) }
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toEntity(Map::class.java)

    private fun found(reference: String): List<*> =
        client
            .get()
            .uri("/mock_openfsp/v1/payments?reference={reference}", reference)
            .header("Authorization", "Bearer $API_KEY")
            .retrieve()
            .body(Map::class.java)!!["data"] as List<*>

    private fun lookup(reference: String) = found(reference).single() as Map<*, *>

    private fun awaitStatus(
        reference: String,
        status: String,
    ) {
        repeat(50) {
            if (lookup(reference)["status"] == status) return
            Thread.sleep(20)
        }
        assertEquals(status, lookup(reference)["status"])
    }

    private fun awaitCallbacks(count: Int) {
        repeat(50) { if (recorder.received.size < count) Thread.sleep(20) }
        assertEquals(count, recorder.received.size)
    }

    private fun verifier(): WebhookVerifier {
        val configuration = client.get().uri("/mock_openfsp/.well-known/openfsp-configuration").retrieve().body(Map::class.java)!!
        val keys = (configuration["webhook_keys"] as List<*>).map { json.convertValue(it, WebhookJwk::class.java) }.associate { it.kid to it.publicKeyOrNull() }
        return WebhookVerifier(keys = { keys[it] }, nonces = InMemoryNonceStore())
    }

    @Test
    fun `mock_openfsp publishes its discovery document and its capabilities`() {
        val configuration = client.get().uri("/mock_openfsp/.well-known/openfsp-configuration").retrieve().body(Map::class.java)!!
        assertEquals("http://127.0.0.1:8090/mock_openfsp/v1", configuration["api_base"])
        val key = (configuration["webhook_keys"] as List<*>).single() as Map<*, *>
        assertEquals("Ed25519", key["crv"])
        assertEquals("active", key["openfsp_status"])
        assertTrue((key["kid"] as String).startsWith("whk_"))

        assertEquals("http://127.0.0.1:8090/mock_openfsp/conformance", configuration["conformance_endpoint"])
        val fixtures = client.post().uri("/mock_openfsp/conformance").retrieve().toEntity(Map::class.java)
        assertEquals(201, fixtures.statusCode.value())
        assertTrue((fixtures.body!!["api_key"] as String).startsWith("ofsp_test_"))
        assertTrue(fixtures.body!!["api_key"] != API_KEY)
        assertEquals(setOf("restricted-key", "statements-key", "revoked-key", "expired-key", "bounded-provider"), (fixtures.body!!["inputs"] as Map<*, *>).keys)
        assertEquals(404, client.post().uri("/mock_moncash/conformance").retrieve().toBodilessEntity().statusCode.value())

        assertEquals(401, client.get().uri("/mock_openfsp/v1/capabilities").retrieve().toBodilessEntity().statusCode.value())
        val capabilities = client.get().uri("/mock_openfsp/v1/capabilities").header("Authorization", "Bearer $API_KEY").retrieve().toEntity(Map::class.java)
        assertTrue(capabilities.headers.cacheControl!!.contains("max-age"))
        val provider = (capabilities.body!!["providers"] as List<*>).single() as Map<*, *>
        assertEquals("mock_openfsp", provider["id"])
        assertEquals("OpenFSP (mock)", provider["display_name"])
        assertEquals(listOf("payments", "payments.fee", "payments.lookup", "payments.statement", "webhooks.emit", "webhooks.per_payment_url"), provider["capabilities"])
        val payments = provider["payments"] as Map<*, *>
        assertEquals(listOf("payer_approval", "redirect"), payments["next_actions"])
        assertEquals(listOf("en", "fr", "ht"), payments["locales"])
        assertEquals(404, client.get().uri("/mock_moncash/.well-known/openfsp-configuration").retrieve().toBodilessEntity().statusCode.value())
    }

    @Test
    fun `an amount outside the declared bounds is refused with amount-out-of-range and the limits`() {
        val capabilities = client.get().uri("/mock_openfsp/v1/capabilities").header("Authorization", "Bearer $API_KEY").retrieve().body(Map::class.java)!!
        val limits = (((capabilities["providers"] as List<*>).single() as Map<*, *>)["payments"] as Map<*, *>)["limits"] as Map<*, *>
        assertEquals(mapOf("amount" to 1000, "currency" to "HTG"), limits["min_amount"])

        val refused = create("bounds1", body = request("bounds1") - "callback_url" + ("amount" to mapOf("amount" to 1050, "currency" to "HTG")))

        assertEquals(422, refused.statusCode.value())
        assertEquals("https://openfsp.org/problems/amount-out-of-range", refused.body!!["type"])
        assertEquals(limits["min_amount"], refused.body!!["min_amount"])
        assertEquals(limits["max_amount"], refused.body!!["max_amount"])
        assertEquals(limits["amount_step"], refused.body!!["amount_step"])
        assertEquals(422, create("bounds2", body = request("bounds2") - "callback_url" + ("amount" to mapOf("amount" to 900, "currency" to "HTG"))).statusCode.value())
        assertEquals(201, create("bounds3", body = request("bounds3") - "callback_url" + ("amount" to mapOf("amount" to 1000, "currency" to "HTG"))).statusCode.value())
    }

    @Test
    fun `a payment without a declared scenario succeeds and sends one signed event to its callback URL`() {
        val created = create("order1")
        assertEquals(201, created.statusCode.value())
        assertTrue(created.headers.getFirst("Request-Id")!!.startsWith("req_"))
        val payment = created.body!!
        assertEquals("pending", payment["status"])
        assertEquals("payer_approval", (payment["next_action"] as Map<*, *>)["type"])
        assertEquals(mapOf("amount" to 125000, "currency" to "HTG"), payment["amount"])
        assertTrue("fee" !in payment)
        assertTrue(created.headers.location.toString().endsWith("/mock_openfsp/v1/payments/${payment["id"]}"))
        assertEquals("ht", state.payments(ControlScope.Instance).single().locale)

        awaitStatus("order1", "succeeded")
        awaitCallbacks(1)
        val callback = recorder.received.single()
        val event = json.readValue(callback.body, Map::class.java)
        assertEquals("payment.succeeded", event["type"])
        assertEquals("pending", event["previous_status"])
        assertEquals("succeeded", (event["data"] as Map<*, *>)["status"])
        assertEquals(MERCHANT_FEE, (event["data"] as Map<*, *>)["fee"])
        assertTrue(callback.headers.getFirst("Signature-Input")!!.contains("tag=\"openfsp-webhook\""))
        assertIs<Verification.Verified>(verifier().verify("POST", callbackUrl, { callback.headers.getFirst(it) }, callback.body))
        val succeeded = lookup("order1")
        assertTrue(succeeded["completed_at"] != null && succeeded["next_action"] == null)
        assertEquals(MERCHANT_FEE, succeeded["fee"])
    }

    @Test
    fun `the control surface reports a payment state by its reference`() {
        val created = create("state1").body!!
        awaitStatus("state1", "succeeded")
        awaitCallbacks(1)

        val state = client.get().uri("/control/operators/mock_openfsp/references/state1").retrieve().body(Map::class.java)!!

        assertEquals(mapOf("payment_id" to created["id"], "settled" to true, "callbacks_sent" to 1), state)
        assertEquals(404, client.get().uri("/control/operators/mock_openfsp/references/unknown").retrieve().toBodilessEntity().statusCode.value())
    }

    @Test
    fun `mock_openfsp keeps the metadata of a payment and returns it on every read`() {
        val metadata = mapOf("order" to "184", "shop" to "Pétion-Ville")
        val created = create("order-metadata", body = request("order-metadata") + ("metadata" to metadata))
        assertEquals(metadata, created.body!!["metadata"])
        assertEquals(metadata, lookup("order-metadata")["metadata"])
        assertEquals(null, create("order-plain").body!!["metadata"])
    }

    @Test
    fun `a duplicated callback repeats the same event byte for byte`() {
        state.declare("mock_openfsp", Accounts.PUBLISHED, "dup1", Scenario.DUPLICATE_CALLBACK)
        create("dup1")
        awaitCallbacks(3)
        val bodies = recorder.received.map { it.body.decodeToString() }.toSet()
        assertEquals(1, bodies.size)
        assertEquals(3, recorder.received.map { it.headers.getFirst("Signature-Input") }.toSet().size)
    }

    @Test
    fun `a declared decline fails with its reason and the operator detail`() {
        state.declare("mock_openfsp", Accounts.PUBLISHED, "order2", Scenario.DECLINE)
        create("order2")
        awaitStatus("order2", "failed")
        val failed = lookup("order2")
        assertEquals("declined", failed["failure_reason"])
        assertEquals("DECLINED", (failed["failure_detail"] as Map<*, *>)["code"])
        assertTrue("fee" !in failed)
    }

    @Test
    fun `a pending payment waits for the control surface`() {
        state.declare("mock_openfsp", Accounts.PUBLISHED, "order3", Scenario.PENDING_FOREVER)
        val paymentId = create("order3").body!!["id"] as String
        Thread.sleep(50)
        assertEquals("pending", lookup("order3")["status"])
        client
            .post()
            .uri("/control/payments/{id}/resolve", paymentId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("outcome" to "SUCCESSFUL"))
            .retrieve()
            .toBodilessEntity()
        val read = client.get().uri("/mock_openfsp/v1/payments/{id}", paymentId).header("Authorization", "Bearer $API_KEY").retrieve().body(Map::class.java)!!
        assertEquals("succeeded", read["status"])
        val unknown = client.get().uri("/mock_openfsp/v1/payments/pay_unknown").header("Authorization", "Bearer $API_KEY").retrieve().toEntity(Map::class.java)
        assertEquals(404, unknown.statusCode.value())
        assertEquals("https://openfsp.org/problems/not-found", unknown.body!!["type"])
    }

    @Test
    fun `a default scenario applies to payments without their own`() {
        state.declareDefault("mock_openfsp", Accounts.PUBLISHED, Scenario.INSUFFICIENT)
        create("order6")
        awaitStatus("order6", "failed")
        assertEquals("insufficient_funds", lookup("order6")["failure_reason"])
    }

    @Test
    fun `a timeout then nothing leaves no payment at the operator`() {
        state.declare("mock_openfsp", Accounts.PUBLISHED, "order7", Scenario.TIMEOUT_THEN_NOTHING)
        create("order7")
        assertTrue(found("order7").isEmpty())
        state.declare("mock_openfsp", Accounts.PUBLISHED, "order7", Scenario.SUCCESS)
        val retried = create("order7")
        assertEquals(201, retried.statusCode.value())
        assertEquals(null, retried.headers.getFirst("Idempotent-Replay"))
        assertEquals(1, found("order7").size)
    }

    @Test
    fun `a contradicting operator reports success then failure`() {
        state.declare("mock_openfsp", Accounts.PUBLISHED, "order8", Scenario.CONTRADICT)
        create("order8")
        awaitStatus("order8", "succeeded")
        assertEquals("failed", lookup("order8")["status"])
    }

    @Test
    fun `an unknown status is reported and no callback is sent`() {
        state.declare("mock_openfsp", Accounts.PUBLISHED, "order9", Scenario.UNKNOWN_STATUS)
        create("order9")
        awaitStatus("order9", "in_review")
        assertTrue(recorder.received.isEmpty())
    }

    @Test
    fun `a provider error is a problem that is not recorded for the key`() {
        state.declare("mock_openfsp", Accounts.PUBLISHED, "error1", Scenario.PROVIDER_ERROR)
        val failed = create("error1")
        assertEquals(502, failed.statusCode.value())
        assertEquals("https://openfsp.org/problems/provider-error", failed.body!!["type"])
        state.declare("mock_openfsp", Accounts.PUBLISHED, "error1", Scenario.SUCCESS)
        assertEquals(201, create("error1").statusCode.value())
    }

    private fun statement(query: String): ResponseEntity<Map<*, *>> =
        client
            .get()
            .uri("/mock_openfsp/v1/statements?$query")
            .header("Authorization", "Bearer $API_KEY")
            .retrieve()
            .onStatus({ true }) { _, _ -> }
            .toEntity(Map::class.java)

    private fun entries(query: String): List<Map<*, *>> = (statement(query).body!!["data"] as List<*>).map { it as Map<*, *> }

    @Test
    fun `mock_openfsp lists its succeeded payments on the statement of their business day`() {
        val paymentId = create("open1").body!!["id"]
        state.declare("mock_openfsp", Accounts.PUBLISHED, "open1-declined", Scenario.DECLINE)
        create("open1-declined")
        val day = clock.today()
        repeat(50) { if (state.payments(ControlScope.Instance).any { !it.isSettled }) Thread.sleep(20) }

        val entry = entries("provider=mock_openfsp&business_date=$day").single { it["reference"] == "open1" }
        assertEquals(paymentId, entry["payment"])
        assertEquals("mock_openfsp", entry["provider"])
        assertEquals(day.toString(), entry["business_date"])
        assertEquals(mapOf("amount" to 125000, "currency" to "HTG"), entry["amount"])
        assertTrue((entry["booked_at"] as String).endsWith("Z"))
        assertEquals(MERCHANT_FEE, entry["fee"])
        assertTrue(entries("provider=mock_openfsp&business_date=$day").none { it["reference"] == "open1-declined" })
        assertTrue(entries("provider=mock_openfsp&from=${day.minusDays(30)}&to=$day").any { it["payment"] == paymentId })
        assertTrue(entries("provider=mock_openfsp&from=${day.minusDays(2)}&to=${day.minusDays(1)}").isEmpty())
        assertTrue(entries("provider=mock_natcash&business_date=$day").isEmpty())
    }

    @Test
    fun `the statement is paged in booking order with an opaque cursor`() {
        listOf("page1", "page2", "page3").forEach { create(it) }
        repeat(50) { if (state.payments(ControlScope.Instance).any { !it.isSettled }) Thread.sleep(20) }
        val day = clock.today()

        val first = statement("provider=mock_openfsp&business_date=$day&limit=2").body!!
        val second = statement("provider=mock_openfsp&business_date=$day&limit=2&cursor=${first["next_cursor"]}").body!!
        val all = entries("provider=mock_openfsp&business_date=$day")

        assertEquals(all.take(2), first["data"])
        assertEquals(all.drop(2).take(2), second["data"])
        assertEquals(all.map { it["booked_at"] as String }.sorted(), all.map { it["booked_at"] })
        assertTrue("next_cursor" !in statement("provider=mock_openfsp&business_date=$day").body!!)
    }

    @Test
    fun `a statement request without a period or with an unreadable one is refused`() {
        val refusal = { query: String -> statement(query).let { it.statusCode.value() to (it.body!!["type"] as String).substringAfterLast('/') } }

        assertEquals(400 to "missing-field", refusal("provider=mock_openfsp"))
        assertEquals(400 to "missing-field", refusal("business_date=2026-09-28"))
        assertEquals(422 to "invalid-field", refusal("provider=mock_openfsp&from=2026-09-01"))
        assertEquals(422 to "invalid-field", refusal("provider=mock_openfsp&business_date=2026-09-01&from=2026-09-01&to=2026-09-02"))
        assertEquals(422 to "invalid-field", refusal("provider=mock_openfsp&business_date=28-09-2026"))
        assertEquals(422 to "invalid-field", refusal("provider=mock_openfsp&from=2026-09-02&to=2026-09-01"))
        assertEquals(422 to "invalid-field", refusal("provider=mock_openfsp&from=2026-09-01&to=2026-10-02"))
        assertEquals(200, statement("provider=mock_openfsp&from=2026-09-01&to=2026-10-01").statusCode.value())
        assertEquals(422 to "invalid-field", refusal("provider=mock_openfsp&business_date=2026-09-28&limit=101"))
        assertEquals(422 to "invalid-field", refusal("provider=mock_openfsp&business_date=2026-09-28&cursor=later"))
    }

    @Test
    fun `a native operator without the statement capability refuses statements with capability-not-supported`() {
        val response =
            client
                .get()
                .uri("/mock_no_source/v1/statements?provider=mock_no_source&business_date={day}", clock.localNow().toLocalDate())
                .header("Authorization", "Bearer $NO_SOURCE_API_KEY")
                .retrieve()
                .toEntity(Map::class.java)
        assertEquals(422, response.statusCode.value())
        assertEquals("https://openfsp.org/problems/capability-not-supported", response.body!!["type"])
    }

    @Test
    fun `a native operator without the per-payment URL capability refuses a callback_url with capability-not-supported`() {
        val response =
            client
                .post()
                .uri("/mock_no_source/v1/payments")
                .header("Authorization", "Bearer $NO_SOURCE_API_KEY")
                .header("Idempotency-Key", "no-callback-1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("reference" to "no-callback-1", "amount" to mapOf("amount" to 125000, "currency" to "HTG"), "provider" to "mock_no_source", "callback_url" to "https://merchant.example/hooks"))
                .retrieve()
                .toEntity(Map::class.java)
        assertEquals(422, response.statusCode.value())
        assertEquals("https://openfsp.org/problems/capability-not-supported", response.body!!["type"])
    }

    @Test
    fun `a native operator without lookup still finds a payment by its reference`() {
        val created =
            client
                .post()
                .uri("/mock_no_source/v1/payments")
                .header("Authorization", "Bearer $NO_SOURCE_API_KEY")
                .header("Idempotency-Key", "by-reference-1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("reference" to "by-reference-1", "amount" to mapOf("amount" to 125000, "currency" to "HTG"), "provider" to "mock_no_source"))
                .retrieve()
                .body(Map::class.java)!!
        val found = client.get().uri("/mock_no_source/v1/payments?reference=by-reference-1").header("Authorization", "Bearer $NO_SOURCE_API_KEY").retrieve().body(Map::class.java)!!
        assertEquals(listOf(created["id"]), (found["data"] as List<*>).map { (it as Map<*, *>)["id"] })
    }

    @Test
    fun `the automatic mode rotates the realistic scenarios and keeps one per order`() {
        state.rotate("mock_openfsp", Accounts.PUBLISHED, true)
        create("auto1")
        create("auto2")
        create("auto1")
        val scenarios = state.payments(ControlScope.Instance).filter { it.operator == "mock_openfsp" }.associate { it.reference to it.scenario }
        assertEquals(Scenario.SUCCESS, scenarios["auto1"])
        assertEquals(Scenario.DECLINE, scenarios["auto2"])
        val declared = client.get().uri("/control/operators/mock_openfsp/default-scenario").retrieve().body(Map::class.java)!!
        assertEquals(true, declared["automatic"])
        state.rotate("mock_openfsp", Accounts.PUBLISHED, false)
        assertEquals(false, state.rotates("mock_openfsp", Accounts.PUBLISHED))
    }

    @Test
    fun `each simulated operator only offers the behaviors of the real one`() {
        val moncash = client.get().uri("/control/operators/mock_moncash/scenarios").retrieve().body(Map::class.java)!!["data"] as List<*>
        assertTrue("PENDING_FOREVER" in moncash)
        assertTrue("DUPLICATE_CALLBACK" in moncash)
        assertTrue("DECLINE" !in moncash)
        val natcash = client.get().uri("/control/operators/mock_natcash/scenarios").retrieve().body(Map::class.java)!!["data"] as List<*>
        assertTrue("DUPLICATE_CALLBACK" !in natcash)
        assertTrue("NO_CALLBACK" in natcash)
        assertTrue("CONTRADICT" !in natcash)
        val refused =
            client
                .put()
                .uri("/control/operators/mock_moncash/default-scenario")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("scenario" to "DECLINE"))
                .exchange { _, response -> response.statusCode.value() }
        assertEquals(422, refused)
        assertEquals(Scenario.SUCCESS, state.defaultFor("mock_moncash", Accounts.PUBLISHED))
    }

    @Test
    fun `the control surface pages and filters the simulated payments`() {
        repeat(3) { create("paged$it") }
        val page = client.get().uri("/control/payments?operator=mock_openfsp&q=paged&size=2&sort=created_at&order=asc").retrieve().body(Map::class.java)!!
        val info = page["page"] as Map<*, *>
        assertEquals(2, (page["data"] as List<*>).size)
        assertEquals(3, info["total_elements"])
        assertEquals(2, info["total_pages"])
        assertEquals("paged0", ((page["data"] as List<*>).first() as Map<*, *>)["order_id"])
    }

    @Test
    fun `the journal keeps the metadata of each exchange without its credentials`() {
        create("meta1")
        val entry = state.journal(ControlScope.Instance, "mock_openfsp").last()
        assertEquals(201, entry.status)
        assertEquals("[REDACTED]", entry.headers["authorization"])
        assertTrue(entry.responseBody.contains("meta1"))
        assertTrue(entry.durationMs >= 0)
    }

    @Test
    fun `the control surface pages the request journal newest first`() {
        create("journal1")
        val page = client.get().uri("/control/journal?operator=mock_openfsp&q=payments&size=1").retrieve().body(Map::class.java)!!
        assertEquals(1, (page["data"] as List<*>).size)
        assertTrue(((page["page"] as Map<*, *>)["total_elements"] as Int) >= 1)
    }

    @Test
    fun `mock_openfsp hosts the checkout of a payment without payer and returns to its return URL`() {
        state.declare("mock_openfsp", Accounts.PUBLISHED, "open2", Scenario.PENDING_FOREVER)
        val created = create("open2", body = request("open2", payer = false) + ("return_url" to "https://shop.example/orders/open2")).body!!
        val nextAction = created["next_action"] as Map<*, *>
        assertEquals("redirect", nextAction["type"])
        assertTrue((nextAction["redirect_url"] as String).endsWith("/mock_openfsp/checkout/${created["id"]}"))
        assertTrue(nextAction["expires_at"] != null)
        val paid = client.post().uri("/mock_openfsp/checkout/{id}?decision=cancel", created["id"]).retrieve().toBodilessEntity()
        assertEquals("https://shop.example/orders/open2", paid.headers.location.toString())
        assertEquals("payer_canceled", lookup("open2")["failure_reason"])
    }

    @Test
    fun `the same key replays the creation and a reference is never created twice`() {
        val first = create("order4")
        val replay = create("order4")
        assertEquals(201, replay.statusCode.value())
        assertEquals("true", replay.headers.getFirst("Idempotent-Replay"))
        assertEquals(first.body, replay.body)

        val conflict = create("order4", idempotencyKey = "another-key")
        assertEquals(409, conflict.statusCode.value())
        assertEquals("https://openfsp.org/problems/reference-conflict", conflict.body!!["type"])
        val replayedConflict = create("order4", idempotencyKey = "another-key")
        assertEquals("true", replayedConflict.headers.getFirst("Idempotent-Replay"))
        assertNotEquals(conflict.body!!["request_id"], replayedConflict.headers.getFirst("Request-Id"))
        assertEquals(conflict.body!!["request_id"], replayedConflict.body!!["request_id"])
        val reused = create("order4", body = request("order4") + ("description" to "changed"))
        assertEquals("https://openfsp.org/problems/idempotency-key-reused", reused.body!!["type"])
        val keyless = create("order11", idempotencyKey = null)
        assertEquals("https://openfsp.org/problems/idempotency-key-required", keyless.body!!["type"])
        assertTrue(found("order11").isEmpty())
    }

    @Test
    fun `a creation outside the OpenFSP request shape is refused field by field`() {
        val unknown = create("bad1", body = request("bad1") + ("colour" to "red"))
        assertEquals(400, unknown.statusCode.value())
        assertEquals("https://openfsp.org/problems/unknown-field", unknown.body!!["type"])
        assertEquals("/colour", ((unknown.body!!["errors"] as List<*>).single() as Map<*, *>)["field"])

        val fee = create("bad5", body = request("bad5") + ("fee" to mapOf("amount" to mapOf("amount" to 0, "currency" to "HTG"), "bearer" to "payer")))
        assertEquals(400, fee.statusCode.value())
        assertEquals("https://openfsp.org/problems/unknown-field", fee.body!!["type"])
        assertEquals("/fee", ((fee.body!!["errors"] as List<*>).single() as Map<*, *>)["field"])

        val nested = create("bad4", body = request("bad4") + mapOf("amount" to mapOf("amount" to 125000, "currency" to "HTG", "precision" to 2), "payer" to mapOf("phone_number" to "+50934567890", "name" to "Ti Jak")))
        assertEquals(400, nested.statusCode.value())
        assertEquals("https://openfsp.org/problems/unknown-field", nested.body!!["type"])
        assertEquals(setOf("/amount/precision", "/payer/name"), (nested.body!!["errors"] as List<*>).map { (it as Map<*, *>)["field"] }.toSet())

        val invalid = create("bad2", body = request("bad2") + mapOf("amount" to mapOf("amount" to 0, "currency" to "HTG"), "locale" to "es", "provider" to "mock_natcash"))
        assertEquals(422, invalid.statusCode.value())
        assertEquals(setOf("/amount/amount", "/locale", "/provider"), (invalid.body!!["errors"] as List<*>).map { (it as Map<*, *>)["field"] }.toSet())

        val missing = create("bad3", body = mapOf("reference" to "bad3"))
        assertEquals("https://openfsp.org/problems/missing-field", missing.body!!["type"])
        assertTrue(state.payments(ControlScope.Instance).isEmpty())
    }

    @Test
    fun `an http callback URL is refused unless it targets a loopback host or a declared development host`() {
        listOf("http://gateway.example/callbacks/mock_openfsp", "http://10.0.0.5/callbacks/mock_openfsp", "http://localhost.example/callbacks").forEachIndexed { index, url ->
            val refused = create("http-cb-$index", body = request("http-cb-$index") + ("callback_url" to url))
            assertEquals(422, refused.statusCode.value(), url)
            assertEquals("https://openfsp.org/problems/invalid-field", refused.body!!["type"])
            val error = (refused.body!!["errors"] as List<*>).single() as Map<*, *>
            assertEquals("/callback_url", error["field"])
            assertEquals("not_allowed", error["code"])
        }
        listOf(
            "http://localhost:1/callbacks",
            "http://127.0.0.2:1/callbacks",
            "http://[::1]:1/callbacks",
            "http://gateway:1/callbacks",
            "https://127.0.0.1:1/callbacks",
        ).forEachIndexed { index, url ->
            state.declare("mock_openfsp", Accounts.PUBLISHED, "loop-cb-$index", Scenario.PENDING_FOREVER)
            assertEquals(201, create("loop-cb-$index", body = request("loop-cb-$index") + ("callback_url" to url)).statusCode.value(), url)
        }
    }

    @Test
    fun `mock_openfsp answers a wrong method and an unknown route with a problem`() {
        val wrongMethod = client.delete().uri("/mock_openfsp/v1/payments").header("Authorization", "Bearer $API_KEY").retrieve().toEntity(Map::class.java)
        assertEquals(405, wrongMethod.statusCode.value())
        assertEquals("application/problem+json", wrongMethod.headers.contentType.toString())
        assertEquals("https://openfsp.org/problems/method-not-allowed", wrongMethod.body!!["type"])
        assertEquals(false, wrongMethod.body!!["retryable"])
        assertEquals("none", wrongMethod.body!!["effect"])
        assertEquals(setOf("GET", "POST"), wrongMethod.headers.allow.map { it.name() }.toSet())

        val unknown = client.get().uri("/mock_openfsp/v1/refunds").header("Authorization", "Bearer $API_KEY").retrieve().toEntity(Map::class.java)
        assertEquals(404, unknown.statusCode.value())
        assertEquals("application/problem+json", unknown.headers.contentType.toString())
        assertEquals("https://openfsp.org/problems/not-found", unknown.body!!["type"])
        assertEquals("/mock_openfsp/v1/refunds", unknown.body!!["instance"])
        assertTrue(unknown.headers.getFirst("Request-Id")!!.startsWith("req_"))
    }

    @Test
    fun `a wrong api key is refused and still journaled`() {
        val refused = create("order5", key = "wrong")
        assertEquals(401, refused.statusCode.value())
        assertEquals("Bearer", refused.headers.getFirst("WWW-Authenticate"))
        val journal = state.journal(ControlScope.Instance, "mock_openfsp")
        assertEquals(1, journal.size)
        assertEquals("POST", journal.single().method)
        assertTrue(journal.single().body.contains("order5"))
    }

    @Test
    fun `the published revoked and expired keys are refused like an unknown key`() {
        val capabilities = { key: String ->
            client.get().uri("/mock_openfsp/v1/capabilities").header("Authorization", "Bearer $key").retrieve().toEntity(Map::class.java)
        }
        val unknown = capabilities("ofsp_test_UnknownKeyForTheSimulatorTests")
        listOf(REVOKED_KEY, EXPIRED_KEY).forEach { key ->
            val refused = capabilities(key)
            assertEquals(401, refused.statusCode.value())
            assertEquals(unknown.body!!["type"], refused.body!!["type"])
            assertEquals(unknown.body!!["title"], refused.body!!["title"])
            assertEquals(unknown.body!!["detail"], refused.body!!["detail"])
        }
        assertEquals(200, capabilities(SECOND_PRINCIPAL_KEY).statusCode.value())
    }

    @Test
    fun `the second principal never receives the recorded response of the first`() {
        val first = create("order-principal")
        assertEquals(201, first.statusCode.value())
        val second = create("order-principal", key = SECOND_PRINCIPAL_KEY)
        assertEquals(409, second.statusCode.value())
        assertEquals(null, second.headers.getFirst("Idempotent-Replay"))
        assertEquals("https://openfsp.org/problems/reference-conflict", second.body!!["type"])
        assertEquals("true", create("order-principal").headers.getFirst("Idempotent-Replay"))
        assertEquals(1, found("order-principal").size)
    }

    @Test
    fun `mock_moncash speaks the MonCash API from token to transaction lookup`() {
        val basic = "Basic " + Base64.getEncoder().encodeToString("mock_moncash_client_id:mock_moncash_client_secret".toByteArray())
        val token =
            client.post().uri("/mock_moncash/Api/oauth/token?grant_type=client_credentials&scope=read,write").header("Authorization", basic).retrieve().body(Map::class.java)!!
        val bearer = "Bearer ${token["access_token"]}"
        assertEquals(401, client.post().uri("/mock_moncash/Api/oauth/token?grant_type=client_credentials").header("Authorization", "Basic eDp5").retrieve().toBodilessEntity().statusCode.value())

        state.declareDefault("mock_moncash", Accounts.PUBLISHED, Scenario.PENDING_FOREVER)
        val created =
            client.post().uri("/mock_moncash/Api/v1/CreatePayment").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .body("""{"amount":1250.5,"orderId":"mc1"}""").retrieve().toEntity(Map::class.java)
        assertEquals(202, created.statusCode.value())
        val paymentToken = (created.body!!["payment_token"] as Map<*, *>)["token"] as String
        val redirect = client.get().uri("/mock_moncash/Moncash-middleware/Payment/Redirect?token={t}", paymentToken).retrieve().toBodilessEntity()
        assertEquals("/mock_moncash/checkout/$paymentToken", redirect.headers.location.toString())

        val retrieve = { client.post().uri("/mock_moncash/Api/v1/RetrieveOrderPayment").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).body("""{"orderId":"mc1"}""").retrieve() }
        assertEquals(404, retrieve().toBodilessEntity().statusCode.value())
        val paid = client.post().uri("/mock_moncash/checkout/{id}?decision=pay", paymentToken).retrieve().toBodilessEntity()
        assertEquals("http://127.0.0.1:8080/return/mock_moncash?transactionId=$paymentToken", paid.headers.location.toString())
        val payment = retrieve().body(Map::class.java)!!["payment"] as Map<*, *>
        assertEquals("successful", payment["message"])
        assertEquals(1250.5, (payment["cost"] as Number).toDouble())
        assertEquals("mc1", payment["reference"])
        val byTransaction =
            client.post().uri("/mock_moncash/Api/v1/RetrieveTransactionPayment").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .body("""{"transactionId":"$paymentToken"}""").retrieve().body(Map::class.java)!!
        assertEquals("mc1", (byTransaction["payment"] as Map<*, *>)["reference"])
    }

    @Test
    fun `mock_natcash checks signatures and returns the payer with a result signed with the function code`() {
        val requestId = "req-1"
        val timestamp = 1_700_000_000_000
        val fields = "MOCKNATCASHmock_natcash_usermock_natcash_password${timestamp}${requestId}nc1100"
        val signature = hmac("mock_natcash_private_key", sha256("mock_natcash_private_key$requestId") + fields)
        val body = { sig: String ->
            mapOf(
                "requestId" to requestId, "partnerCode" to "MOCKNATCASH", "username" to "mock_natcash_user", "password" to "mock_natcash_password",
                "callbackUrl" to "http://127.0.0.1:$port/test/natcash", "timestamp" to timestamp, "orderNumber" to "nc1", "amount" to 100, "signature" to sig,
            )
        }
        val post = { payload: Map<String, Any> ->
            client.post().uri("/mock_natcash/api/online-payment/credential").contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().body(Map::class.java)!!
        }
        assertEquals(101, post(body("00"))["status"])
        val created = post(body(signature))
        assertEquals(0, created["status"])
        val checkout = created["url"] as String
        assertTrue(checkout.contains("/mock_natcash/checkout/"))
        val returned =
            client
                .post()
                .uri(checkout.substringAfter("/mock_natcash/").let { "/mock_natcash/$it" })
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("decision=pay")
                .exchange { _, response -> response.headers.location }!!
        val parameters = UriComponentsBuilder.fromUri(returned).build().queryParams.toSingleValueMap()
        assertEquals("/test/natcash", returned.path)
        assertEquals("nc1", parameters["orderNumber"])
        assertEquals("1", parameters["code"])
        assertEquals(hmac("mock_natcash_function_code", sha256("mock_natcash_function_codenc1") + "nc11"), parameters["signature"])
        assertTrue(recorder.natCash.isEmpty())
    }

    private companion object {
        const val API_KEY = "ofsp_test_MockOpenFspKeyPublishedForTheSimulator"
        const val NO_SOURCE_API_KEY = "ofsp_test_MockNoSourceKeyPublishedForTheSimulator"
        val MERCHANT_FEE = mapOf("amount" to mapOf("amount" to 2500, "currency" to "HTG"), "bearer" to "merchant")
        const val SECOND_PRINCIPAL_KEY = "ofsp_test_MockOpenFspSecondPrincipalKeyForTheSimulator"
        const val REVOKED_KEY = "ofsp_test_MockOpenFspRevokedKeyPublishedForTheSimulator"
        const val EXPIRED_KEY = "ofsp_test_MockOpenFspExpiredKeyPublishedForTheSimulator"
    }
}
