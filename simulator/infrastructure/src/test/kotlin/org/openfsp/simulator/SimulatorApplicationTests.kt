package org.openfsp.simulator

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openfsp.simulator.dialects.openfsp.OpenFspCallbacks
import org.openfsp.simulator.operator.Scenario
import org.openfsp.simulator.operator.SimulationClock
import org.openfsp.simulator.operator.api.ControlSimulation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import java.security.MessageDigest
import java.util.Base64
import java.util.HexFormat
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RestController
class CallbackRecorder {
    val received = CopyOnWriteArrayList<Pair<String, String>>()

    val natCash = CopyOnWriteArrayList<Map<String, Any?>>()

    @PostMapping("/test/natcash")
    fun receiveNatCash(
        @RequestBody body: Map<String, Any?>,
    ) {
        natCash += body
    }

    @PostMapping("/test/callbacks")
    fun receive(
        @RequestHeader(OpenFspCallbacks.SIGNATURE_HEADER) signature: String,
        @RequestBody body: String,
    ) {
        received += signature to body
    }
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "simulator.response-delay=0ms",
        "simulator.settle-after=0ms",
        "simulator.hang-duration=50ms",
        "server.address=127.0.0.1",
    ],
)
class SimulatorApplicationTests {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var state: ControlSimulation

    @Autowired
    private lateinit var clock: SimulationClock

    @Autowired
    private lateinit var recorder: CallbackRecorder

    private val client by lazy {
        RestClient
            .builder()
            .baseUrl("http://127.0.0.1:$port")
            .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
            .build()
    }

    private fun sha256(value: String) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))

    private fun hmac(
        key: String,
        value: String,
    ) = HexFormat.of().formatHex(Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.toByteArray(), "HmacSHA256")) }.doFinal(value.toByteArray()))

    @BeforeEach
    fun reset() {
        state.reset()
        recorder.received.clear()
        recorder.natCash.clear()
    }

    private fun create(
        orderId: String,
        key: String = "mock_openfsp_test_key",
    ) = client
        .post()
        .uri("/mock_openfsp/v1/payments")
        .header("Authorization", "Bearer $key")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            mapOf(
                "order_id" to orderId,
                "amount" to "1250.00",
                "msisdn" to "50934567890",
                "callback_url" to "http://127.0.0.1:$port/test/callbacks",
            ),
        ).retrieve()
        .toEntity(Map::class.java)

    private fun lookup(orderId: String) =
        client
            .get()
            .uri("/mock_openfsp/v1/payments/by-order/{orderId}", orderId)
            .header("Authorization", "Bearer mock_openfsp_test_key")
            .retrieve()
            .body(Map::class.java)!!

    private fun awaitStatus(
        orderId: String,
        status: String,
    ) {
        repeat(50) {
            if (lookup(orderId)["status"] == status) return
            Thread.sleep(20)
        }
        assertEquals(status, lookup(orderId)["status"])
    }

    @Test
    fun `a payment without a declared scenario succeeds and sends one signed callback`() {
        val created = create("order1")
        assertEquals(201, created.statusCode.value())
        assertEquals("PENDING", created.body!!["status"])
        awaitStatus("order1", "SUCCESSFUL")
        repeat(50) { if (recorder.received.isEmpty()) Thread.sleep(20) }
        assertEquals(1, recorder.received.size)
        assertTrue(recorder.received.single().first.startsWith("sha256="))
    }

    @Test
    fun `a declared decline fails with its reason`() {
        state.declare("mock_openfsp", "order2", Scenario.DECLINE)
        create("order2")
        awaitStatus("order2", "FAILED")
        assertEquals("DECLINED", lookup("order2")["reason"])
    }

    @Test
    fun `a pending payment waits for the control surface`() {
        state.declare("mock_openfsp", "order3", Scenario.PENDING_FOREVER)
        val paymentId = create("order3").body!!["payment_id"] as String
        Thread.sleep(50)
        assertEquals("PENDING", lookup("order3")["status"])
        client
            .post()
            .uri("/control/payments/{id}/resolve", paymentId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("outcome" to "SUCCESSFUL"))
            .retrieve()
            .toBodilessEntity()
        assertEquals("SUCCESSFUL", lookup("order3")["status"])
    }

    @Test
    fun `a default scenario applies to payments without their own`() {
        state.declareDefault("mock_openfsp", Scenario.INSUFFICIENT)
        create("order6")
        awaitStatus("order6", "FAILED")
        assertEquals("INSUFFICIENT_FUNDS", lookup("order6")["reason"])
    }

    @Test
    fun `a timeout then nothing leaves no payment at the operator`() {
        state.declare("mock_openfsp", "order7", Scenario.TIMEOUT_THEN_NOTHING)
        create("order7")
        assertEquals(404, client.get().uri("/mock_openfsp/v1/payments/by-order/order7").header("Authorization", "Bearer mock_openfsp_test_key").retrieve().toBodilessEntity().statusCode.value())
    }

    @Test
    fun `a contradicting operator reports success then failure`() {
        state.declare("mock_openfsp", "order8", Scenario.CONTRADICT)
        create("order8")
        awaitStatus("order8", "SUCCESSFUL")
        assertEquals("FAILED", lookup("order8")["status"])
    }

    @Test
    fun `an unknown status is reported and no callback is sent`() {
        state.declare("mock_openfsp", "order9", Scenario.UNKNOWN_STATUS)
        create("order9")
        awaitStatus("order9", "IN_REVIEW")
        assertTrue(recorder.received.isEmpty())
    }

    @Test
    fun `credential echo returns the received authorization in the error`() {
        state.declare("mock_openfsp", "order10", Scenario.CREDENTIAL_ECHO)
        val response = create("order10")
        assertEquals(400, response.statusCode.value())
        assertTrue((response.body!!["message"] as String).contains("mock_openfsp_test_key"))
    }

    @Test
    fun `mock_openfsp books succeeded payments on its daily statement`() {
        create("open1")
        val day = clock.localNow().toLocalDate()
        repeat(50) { if (state.statementLines("mock_openfsp", day).isEmpty()) Thread.sleep(20) }
        state.closeDay("mock_openfsp", day)
        val statement =
            client.get().uri("/mock_openfsp/v1/statements/{day}", day).header("Authorization", "Bearer mock_openfsp_test_key").retrieve().body(Map::class.java)!!
        assertEquals(true, statement["closed"])
        val line = (statement["entries"] as List<*>).single() as Map<*, *>
        assertEquals("open1", line["order_id"])
        assertTrue(Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}$").matches(line["booked_at"] as String))
    }

    @Test
    fun `mock_openfsp hosts the checkout of a payment without payer`() {
        val created =
            client
                .post()
                .uri("/mock_openfsp/v1/payments")
                .header("Authorization", "Bearer mock_openfsp_test_key")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("order_id" to "open2", "amount" to "75.00", "callback_url" to "http://127.0.0.1:$port/test/callbacks"))
                .retrieve()
                .body(Map::class.java)!!
        assertEquals("REDIRECT", created["approval"])
        assertTrue((created["checkout_url"] as String).endsWith("/mock_openfsp/checkout/${created["payment_id"]}"))
    }

    @Test
    fun `mock_moncash speaks the MonCash API from token to transaction lookup`() {
        val basic = "Basic " + Base64.getEncoder().encodeToString("mock_moncash_client_id:mock_moncash_client_secret".toByteArray())
        val token =
            client.post().uri("/mock_moncash/Api/oauth/token?grant_type=client_credentials&scope=read,write").header("Authorization", basic).retrieve().body(Map::class.java)!!
        val bearer = "Bearer ${token["access_token"]}"
        assertEquals(401, client.post().uri("/mock_moncash/Api/oauth/token?grant_type=client_credentials").header("Authorization", "Basic eDp5").retrieve().toBodilessEntity().statusCode.value())

        state.declareDefault("mock_moncash", Scenario.PENDING_FOREVER)
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

    @Test
    fun `creating twice with the same order id returns the same payment`() {
        val first = create("order4").body!!["payment_id"]
        val second = create("order4")
        assertEquals(200, second.statusCode.value())
        assertEquals(first, second.body!!["payment_id"])
    }

    @Test
    fun `a wrong api key is refused and still journaled`() {
        assertEquals(401, create("order5", key = "wrong").statusCode.value())
        val journal = state.journal("mock_openfsp")
        assertEquals(1, journal.size)
        assertEquals("POST", journal.single().method)
        assertTrue(journal.single().body.contains("order5"))
    }
}
