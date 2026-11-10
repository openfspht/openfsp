package org.openfsp.simulator

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openfsp.simulator.adapters.HttpCallbackSender
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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RestController
class CallbackRecorder {
    val received = CopyOnWriteArrayList<Pair<String, String>>()
    val unsigned = CopyOnWriteArrayList<String>()

    @PostMapping("/test/unsigned/{token}")
    fun receiveUnsigned(
        @RequestHeader(HttpCallbackSender.SIGNATURE_HEADER, required = false) signature: String?,
        @RequestBody body: String,
    ) {
        if (signature == null) unsigned += body
    }

    @PostMapping("/test/callbacks")
    fun receive(
        @RequestHeader(HttpCallbackSender.SIGNATURE_HEADER) signature: String,
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

    @BeforeEach
    fun reset() {
        state.reset()
        recorder.received.clear()
        recorder.unsigned.clear()
    }

    private fun create(
        orderId: String,
        key: String = "mock_alpha_test_key",
    ) = client
        .post()
        .uri("/mock_alpha/v1/payments")
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
            .uri("/mock_alpha/v1/payments/by-order/{orderId}", orderId)
            .header("Authorization", "Bearer mock_alpha_test_key")
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
        state.declare("mock_alpha", "order2", Scenario.DECLINE)
        create("order2")
        awaitStatus("order2", "FAILED")
        assertEquals("DECLINED", lookup("order2")["reason"])
    }

    @Test
    fun `a pending payment waits for the control surface`() {
        state.declare("mock_alpha", "order3", Scenario.PENDING_FOREVER)
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
        state.declareDefault("mock_alpha", Scenario.INSUFFICIENT)
        create("order6")
        awaitStatus("order6", "FAILED")
        assertEquals("INSUFFICIENT_FUNDS", lookup("order6")["reason"])
    }

    @Test
    fun `a timeout then nothing leaves no payment at the operator`() {
        state.declare("mock_alpha", "order7", Scenario.TIMEOUT_THEN_NOTHING)
        create("order7")
        assertEquals(404, client.get().uri("/mock_alpha/v1/payments/by-order/order7").header("Authorization", "Bearer mock_alpha_test_key").retrieve().toBodilessEntity().statusCode.value())
    }

    @Test
    fun `a contradicting operator reports success then failure`() {
        state.declare("mock_alpha", "order8", Scenario.CONTRADICT)
        create("order8")
        awaitStatus("order8", "SUCCESSFUL")
        assertEquals("FAILED", lookup("order8")["status"])
    }

    @Test
    fun `an unknown status is reported and no callback is sent`() {
        state.declare("mock_alpha", "order9", Scenario.UNKNOWN_STATUS)
        create("order9")
        awaitStatus("order9", "IN_REVIEW")
        assertTrue(recorder.received.isEmpty())
    }

    @Test
    fun `credential echo returns the received authorization in the error`() {
        state.declare("mock_alpha", "order10", Scenario.CREDENTIAL_ECHO)
        val response = create("order10")
        assertEquals(400, response.statusCode.value())
        assertTrue((response.body!!["message"] as String).contains("mock_alpha_test_key"))
    }

    @Test
    fun `mock_beta offers neither lookup nor callbacks nor idempotency`() {
        val beta = { orderId: String ->
            client
                .post()
                .uri("/mock_beta/v1/payments")
                .header("Authorization", "Bearer mock_beta_test_key")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("order_id" to orderId, "amount" to "10.00", "callback_url" to "http://127.0.0.1:$port/test/callbacks"))
                .retrieve()
                .body(Map::class.java)!!
        }
        val first = beta("beta1")["payment_id"]
        assertTrue(first != beta("beta1")["payment_id"])
        val lookup = client.get().uri("/mock_beta/v1/payments/by-order/beta1").header("Authorization", "Bearer mock_beta_test_key").retrieve()
        assertEquals(404, lookup.toBodilessEntity().statusCode.value())
        Thread.sleep(100)
        assertTrue(recorder.received.isEmpty())
    }

    @Test
    fun `mock_epsilon books succeeded payments on its daily statement and calls back unsigned`() {
        client
            .post()
            .uri("/mock_epsilon/v1/payments")
            .header("Authorization", "Bearer mock_epsilon_test_key")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("order_id" to "eps1", "amount" to "75.00", "callback_url" to "http://127.0.0.1:$port/test/unsigned/token"))
            .retrieve()
            .toBodilessEntity()
        val day = clock.localNow().toLocalDate()
        repeat(50) { if (state.statementLines("mock_epsilon", day).isEmpty()) Thread.sleep(20) }
        state.closeDay("mock_epsilon", day)
        val statement =
            client.get().uri("/mock_epsilon/v1/statements/{day}", day).header("Authorization", "Bearer mock_epsilon_test_key").retrieve().body(Map::class.java)!!
        assertEquals(true, statement["closed"])
        val line = (statement["entries"] as List<*>).single() as Map<*, *>
        assertEquals("eps1", line["order_id"])
        assertTrue(Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}$").matches(line["booked_at"] as String))
        repeat(50) { if (recorder.unsigned.isEmpty()) Thread.sleep(20) }
        assertEquals(1, recorder.unsigned.size)
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
        val journal = state.journal("mock_alpha")
        assertEquals(1, journal.size)
        assertEquals("POST", journal.single().method)
        assertTrue(journal.single().body.contains("order5"))
    }
}
