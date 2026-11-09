package org.openfsp.simulator

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openfsp.simulator.operator.Settlement
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

    @PostMapping("/test/callbacks")
    fun receive(
        @RequestHeader(Settlement.SIGNATURE_HEADER) signature: String,
        @RequestBody body: String,
    ) {
        received += signature to body
    }
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["simulator.response-delay=0ms", "simulator.settle-after=0ms", "server.address=127.0.0.1"],
)
class SimulatorApplicationTests {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var state: SimulatorState

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
