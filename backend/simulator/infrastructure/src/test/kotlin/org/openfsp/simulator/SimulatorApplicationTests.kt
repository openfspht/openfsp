package org.openfsp.simulator

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openfsp.protocol.webhook.InMemoryNonceStore
import org.openfsp.protocol.webhook.Verification
import org.openfsp.protocol.webhook.WebhookJwk
import org.openfsp.protocol.webhook.WebhookVerifier
import org.openfsp.simulator.controllers.ControlController
import org.openfsp.simulator.operator.SimulationClock
import org.openfsp.simulator.operator.control.api.ControlSimulation
import org.openfsp.simulator.operator.scenario.Scenario
import org.openfsp.simulator.operator.space.SpaceId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.util.Base64
import java.util.HexFormat
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
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
        "simulator.response-delay=0ms",
        "simulator.settle-after=0ms",
        "simulator.hang-duration=50ms",
        "server.address=127.0.0.1",
        "simulator.http-callback-hosts=gateway",
    ],
)
class SimulatorApplicationTests {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var state: ControlSimulation

    @Autowired
    private lateinit var control: ControlController

    @Autowired
    private lateinit var clock: SimulationClock

    @Autowired
    private lateinit var recorder: CallbackRecorder

    @Autowired
    private lateinit var json: JsonMapper

    private val client by lazy {
        RestClient
            .builder()
            .baseUrl("http://127.0.0.1:$port")
            .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
            .build()
    }

    private val callbackUrl get() = "http://127.0.0.1:$port/test/callbacks"

    private fun sha256(value: String) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))

    private fun hmac(
        key: String,
        value: String,
    ) = HexFormat.of().formatHex(Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.toByteArray(), "HmacSHA256")) }.doFinal(value.toByteArray()))

    @BeforeEach
    fun reset() {
        control.reset(SpaceId.DEFAULT)
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

        assertEquals(401, client.get().uri("/mock_openfsp/v1/capabilities").retrieve().toBodilessEntity().statusCode.value())
        val capabilities = client.get().uri("/mock_openfsp/v1/capabilities").header("Authorization", "Bearer $API_KEY").retrieve().toEntity(Map::class.java)
        assertTrue(capabilities.headers.cacheControl!!.contains("max-age"))
        val provider = (capabilities.body!!["providers"] as List<*>).single() as Map<*, *>
        assertEquals("mock_openfsp", provider["id"])
        assertEquals("OpenFSP (mock)", provider["display_name"])
        assertEquals(listOf("payments", "payments.lookup", "payments.statement", "webhooks.per_payment_url", "webhooks.verify"), provider["capabilities"])
        val payments = provider["payments"] as Map<*, *>
        assertEquals(listOf("payer_approval", "redirect"), payments["next_actions"])
        assertEquals(listOf("en", "fr", "ht"), payments["locales"])
        assertEquals(404, client.get().uri("/mock_moncash/.well-known/openfsp-configuration").retrieve().toBodilessEntity().statusCode.value())
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
        assertTrue(created.headers.location.toString().endsWith("/mock_openfsp/v1/payments/${payment["id"]}"))
        assertEquals("ht", state.payments(SpaceId.DEFAULT).single().locale)

        awaitStatus("order1", "succeeded")
        awaitCallbacks(1)
        val callback = recorder.received.single()
        val event = json.readValue(callback.body, Map::class.java)
        assertEquals("payment.succeeded", event["type"])
        assertEquals("pending", event["previous_status"])
        assertEquals("succeeded", (event["data"] as Map<*, *>)["status"])
        assertTrue(callback.headers.getFirst("Signature-Input")!!.contains("tag=\"openfsp-webhook\""))
        assertIs<Verification.Verified>(verifier().verify("POST", callbackUrl, { callback.headers.getFirst(it) }, callback.body))
        val succeeded = lookup("order1")
        assertTrue(succeeded["completed_at"] != null && succeeded["next_action"] == null)
    }

    @Test
    fun `a duplicated callback repeats the same event byte for byte`() {
        state.declare(SpaceId.DEFAULT, "mock_openfsp", "dup1", Scenario.DUPLICATE_CALLBACK)
        create("dup1")
        awaitCallbacks(3)
        val bodies = recorder.received.map { it.body.decodeToString() }.toSet()
        assertEquals(1, bodies.size)
        assertEquals(3, recorder.received.map { it.headers.getFirst("Signature-Input") }.toSet().size)
    }

    @Test
    fun `a declared decline fails with its reason and the operator detail`() {
        state.declare(SpaceId.DEFAULT, "mock_openfsp", "order2", Scenario.DECLINE)
        create("order2")
        awaitStatus("order2", "failed")
        val failed = lookup("order2")
        assertEquals("declined", failed["failure_reason"])
        assertEquals("DECLINED", (failed["failure_detail"] as Map<*, *>)["code"])
    }

    @Test
    fun `a pending payment waits for the control surface`() {
        state.declare(SpaceId.DEFAULT, "mock_openfsp", "order3", Scenario.PENDING_FOREVER)
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
        val synchronized = client.post().uri("/mock_openfsp/v1/payments/{id}/synchronize", paymentId).header("Authorization", "Bearer $API_KEY").retrieve().body(Map::class.java)!!
        assertEquals("succeeded", synchronized["status"])
        val unknown = client.get().uri("/mock_openfsp/v1/payments/MO-unknown").header("Authorization", "Bearer $API_KEY").retrieve().toEntity(Map::class.java)
        assertEquals(404, unknown.statusCode.value())
        assertEquals("https://openfsp.org/problems/not-found", unknown.body!!["type"])
    }

    @Test
    fun `a default scenario applies to payments without their own`() {
        state.declareDefault(SpaceId.DEFAULT, "mock_openfsp", Scenario.INSUFFICIENT)
        create("order6")
        awaitStatus("order6", "failed")
        assertEquals("insufficient_funds", lookup("order6")["failure_reason"])
    }

    @Test
    fun `a timeout then nothing leaves no payment at the operator`() {
        state.declare(SpaceId.DEFAULT, "mock_openfsp", "order7", Scenario.TIMEOUT_THEN_NOTHING)
        create("order7")
        assertTrue(found("order7").isEmpty())
        state.declare(SpaceId.DEFAULT, "mock_openfsp", "order7", Scenario.SUCCESS)
        val retried = create("order7")
        assertEquals(201, retried.statusCode.value())
        assertEquals(null, retried.headers.getFirst("Idempotent-Replay"))
        assertEquals(1, found("order7").size)
    }

    @Test
    fun `a contradicting operator reports success then failure`() {
        state.declare(SpaceId.DEFAULT, "mock_openfsp", "order8", Scenario.CONTRADICT)
        create("order8")
        awaitStatus("order8", "succeeded")
        assertEquals("failed", lookup("order8")["status"])
    }

    @Test
    fun `an unknown status is reported and no callback is sent`() {
        state.declare(SpaceId.DEFAULT, "mock_openfsp", "order9", Scenario.UNKNOWN_STATUS)
        create("order9")
        awaitStatus("order9", "in_review")
        assertTrue(recorder.received.isEmpty())
    }

    @Test
    fun `credential echo returns the received authorization in the problem`() {
        state.declare(SpaceId.DEFAULT, "mock_openfsp", "order10", Scenario.CREDENTIAL_ECHO)
        val response = create("order10")
        assertEquals(400, response.statusCode.value())
        assertEquals("application/problem+json", response.headers.contentType.toString())
        assertTrue((response.body!!["detail"] as String).contains(API_KEY))
        assertTrue((response.body!!["request_id"] as String).startsWith("req_"))
    }

    @Test
    fun `a provider error is a problem that is not recorded for the key`() {
        state.declare(SpaceId.DEFAULT, "mock_openfsp", "error1", Scenario.PROVIDER_ERROR)
        val failed = create("error1")
        assertEquals(500, failed.statusCode.value())
        assertEquals("https://openfsp.org/problems/internal-error", failed.body!!["type"])
        state.declare(SpaceId.DEFAULT, "mock_openfsp", "error1", Scenario.SUCCESS)
        assertEquals(201, create("error1").statusCode.value())
    }

    @Test
    fun `mock_openfsp books succeeded payments on its daily statement`() {
        create("open1")
        val day = clock.localNow().toLocalDate()
        repeat(50) { if (state.statementLines(SpaceId.DEFAULT, "mock_openfsp", day).isEmpty()) Thread.sleep(20) }
        state.closeDay(SpaceId.DEFAULT, "mock_openfsp", day)
        val read = { provider: String ->
            client
                .get()
                .uri("/mock_openfsp/v1/statements?provider={provider}&business_date={day}", provider, day)
                .header("Authorization", "Bearer $API_KEY")
                .retrieve()
                .body(Map::class.java)!!["data"] as List<*>
        }
        val statement = read("mock_openfsp").single() as Map<*, *>
        assertEquals(true, statement["complete"])
        assertEquals(day.toString(), statement["business_date"])
        val line = (statement["entries"] as List<*>).single() as Map<*, *>
        assertEquals("open1", line["provider_order_id"])
        assertEquals("payment", line["type"])
        assertEquals(mapOf("amount" to 125000, "currency" to "HTG"), line["amount"])
        assertTrue((line["booked_at"] as String).endsWith("Z"))
        assertTrue(read("mock_natcash").isEmpty())
        val missing = client.get().uri("/mock_openfsp/v1/statements?provider=mock_openfsp").header("Authorization", "Bearer $API_KEY").retrieve().toEntity(Map::class.java)
        assertEquals("https://openfsp.org/problems/missing-field", missing.body!!["type"])
    }

    @Test
    fun `the automatic mode rotates the realistic scenarios and keeps one per order`() {
        state.rotate(SpaceId.DEFAULT, "mock_openfsp", true)
        create("auto1")
        create("auto2")
        create("auto1")
        val scenarios = state.payments(SpaceId.DEFAULT).filter { it.operator == "mock_openfsp" }.associate { it.orderId to it.scenario }
        assertEquals(Scenario.SUCCESS, scenarios["auto1"])
        assertEquals(Scenario.DECLINE, scenarios["auto2"])
        val declared = client.get().uri("/control/operators/mock_openfsp/default-scenario").retrieve().body(Map::class.java)!!
        assertEquals(true, declared["automatic"])
        state.rotate(SpaceId.DEFAULT, "mock_openfsp", false)
        assertEquals(false, state.rotates(SpaceId.DEFAULT, "mock_openfsp"))
    }

    @Test
    fun `each simulated operator only offers the behaviors of the real one`() {
        val moncash = client.get().uri("/control/operators/mock_moncash/scenarios").retrieve().body(Map::class.java)!!["data"] as List<*>
        assertTrue("PENDING_FOREVER" in moncash)
        assertTrue("DUPLICATE_CALLBACK" !in moncash)
        assertTrue("DECLINE" !in moncash)
        val natcash = client.get().uri("/control/operators/mock_natcash/scenarios").retrieve().body(Map::class.java)!!["data"] as List<*>
        assertTrue("DUPLICATE_CALLBACK" in natcash)
        assertTrue("CONTRADICT" !in natcash)
        val refused =
            client
                .put()
                .uri("/control/operators/mock_moncash/default-scenario")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("scenario" to "DUPLICATE_CALLBACK"))
                .exchange { _, response -> response.statusCode.value() }
        assertEquals(422, refused)
        assertEquals(Scenario.SUCCESS, state.defaultFor(SpaceId.DEFAULT, "mock_moncash"))
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
        val entry = state.journal(SpaceId.DEFAULT, "mock_openfsp").last()
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
    fun `the control surface lists the closed days of an operator with the Haitian business day`() {
        val today = clock.today()
        state.closeDay(SpaceId.DEFAULT, "mock_openfsp", today.minusDays(1))
        state.closeDay(SpaceId.DEFAULT, "mock_openfsp", today)
        val days = client.get().uri("/control/operators/mock_openfsp/statements").retrieve().body(Map::class.java)!!
        assertEquals(today.toString(), days["today"])
        assertEquals(listOf(today.toString(), today.minusDays(1).toString()), days["closed"])
    }

    @Test
    fun `mock_openfsp hosts the checkout of a payment without payer and returns to its return URL`() {
        state.declare(SpaceId.DEFAULT, "mock_openfsp", "open2", Scenario.PENDING_FOREVER)
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

        val invalid = create("bad2", body = request("bad2") + mapOf("amount" to mapOf("amount" to 0, "currency" to "HTG"), "locale" to "es", "provider" to "mock_natcash"))
        assertEquals(422, invalid.statusCode.value())
        assertEquals(setOf("/amount/amount", "/locale", "/provider"), (invalid.body!!["errors"] as List<*>).map { (it as Map<*, *>)["field"] }.toSet())

        val missing = create("bad3", body = mapOf("reference" to "bad3"))
        assertEquals("https://openfsp.org/problems/missing-field", missing.body!!["type"])
        assertTrue(state.payments(SpaceId.DEFAULT).isEmpty())
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
            state.declare(SpaceId.DEFAULT, "mock_openfsp", "loop-cb-$index", Scenario.PENDING_FOREVER)
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
        val journal = state.journal(SpaceId.DEFAULT, "mock_openfsp")
        assertEquals(1, journal.size)
        assertEquals("POST", journal.single().method)
        assertTrue(journal.single().body.contains("order5"))
    }

    @Test
    fun `mock_moncash speaks the MonCash API from token to transaction lookup`() {
        val basic = "Basic " + Base64.getEncoder().encodeToString("mock_moncash_client_id:mock_moncash_client_secret".toByteArray())
        val token =
            client.post().uri("/mock_moncash/Api/oauth/token?grant_type=client_credentials&scope=read,write").header("Authorization", basic).retrieve().body(Map::class.java)!!
        val bearer = "Bearer ${token["access_token"]}"
        assertEquals(401, client.post().uri("/mock_moncash/Api/oauth/token?grant_type=client_credentials").header("Authorization", "Basic eDp5").retrieve().toBodilessEntity().statusCode.value())

        state.declareDefault(SpaceId.DEFAULT, "mock_moncash", Scenario.PENDING_FOREVER)
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
    fun `mock_natcash checks signatures and calls back signed with the function code`() {
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
        assertTrue((created["url"] as String).contains("/mock_natcash/checkout/"))
        repeat(50) { if (recorder.natCash.isEmpty()) Thread.sleep(20) }
        val callback = recorder.natCash.single()
        assertEquals("1", callback["code"])
        assertEquals(hmac("mock_natcash_function_code", sha256("mock_natcash_function_codenc1") + "nc11"), callback["signature"])
    }

    private companion object {
        const val API_KEY = "ofsp_test_MockOpenFspKeyPublishedForTheSimulator"
    }
}
