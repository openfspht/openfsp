package org.openfsp.gateway

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.auth.api.ManageKeys
import org.openfsp.protocol.OrderIdDerivation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.net.ServerSocket
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("development", "test")
class MockOpenFspEndToEndTests {
    @Autowired
    private lateinit var json: JsonMapper

    @Autowired
    private lateinit var keys: ManageKeys

    @Autowired
    private lateinit var orderIds: OrderIdDerivation

    private val key by lazy { keys.issue("end-to-end", setOf(Scope.PAYMENTS_READ, Scope.PAYMENTS_WRITE, Scope.CAPABILITIES_READ)).secret }

    private val gateway by lazy {
        RestClient
            .builder()
            .requestFactory(JdkClientHttpRequestFactory())
            .baseUrl("http://127.0.0.1:$GATEWAY_PORT")
            .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
            .build()
    }

    private fun create(reference: String): JsonNode {
        val body = """{"reference":"$reference","amount":{"amount":125000,"currency":"HTG"},"provider":"mock_openfsp","payer":{"phone_number":"+50934567890"},"locale":"ht"}"""
        val response =
            gateway
                .post()
                .uri("/v1/payments")
                .header("Authorization", "Bearer $key")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(ByteArray::class.java)
        assertEquals(201, response.statusCode.value(), response.body?.decodeToString())
        return json.readTree(response.body)
    }

    private fun JsonNode.items(): List<JsonNode> = (0 until size()).map { get(it) }

    private fun read(id: String): JsonNode = json.readTree(gateway.get().uri("/v1/payments/{id}", id).header("Authorization", "Bearer $key").retrieve().body(ByteArray::class.java))

    private fun awaitStatus(
        id: String,
        status: String,
    ): JsonNode {
        repeat(100) {
            val payment = read(id)
            if (payment.get("status").asString() == status) return payment
            Thread.sleep(100)
        }
        val payment = read(id)
        assertEquals(status, payment.get("status").asString())
        return payment
    }

    @Test
    fun `a payment created at mock_openfsp through the OpenFSP adapter is settled by its signed event`() {
        val capabilities = json.readTree(gateway.get().uri("/v1/capabilities").header("Authorization", "Bearer $key").retrieve().body(ByteArray::class.java))
        val announced = capabilities.get("providers").items().first { it.get("id").asString() == "mock_openfsp" }
        assertEquals("OpenFSP (mock)", announced.get("display_name").asString())
        assertTrue(announced.get("capabilities").items().map { it.asString() }.containsAll(listOf("payments.lookup", "payments.statement", "webhooks.per_payment_url", "webhooks.verify")))

        val reference = "INV-${UUID.randomUUID()}"
        val created = create(reference)
        assertEquals("pending", created.get("status").asString())
        assertEquals("payer_approval", created.get("next_action").get("type").asString())
        val orderId = created.get("provider_order_id").asString()
        assertEquals(orderIds.derive(reference), orderId)

        val settled = awaitStatus(created.get("id").asString(), "succeeded")
        assertTrue(settled.get("provider_reference").asString().startsWith("MO-"))
        val detail = json.readTree(gateway.get().uri("/admin/payments/{id}", created.get("id").asString()).retrieve().body(ByteArray::class.java))
        assertEquals(listOf("creation", "signed_callback"), detail.get("transitions").items().map { it.get("source").asString() })

        val journal = json.readTree(simulator.get().uri("/control/journal?operator=mock_openfsp&q={q}&size=50", orderId).retrieve().body(ByteArray::class.java)).get("data")
        val creation = journal.items().first { it.get("method").asString() == "POST" && it.get("path").asString() == "/mock_openfsp/v1/payments" }
        assertEquals("create-$orderId", creation.get("headers").get("idempotency-key").asString())
        assertTrue(creation.get("body").asString().contains("\"reference\":\"$orderId\""))
        val simulated = json.readTree(simulator.get().uri("/control/payments?q={q}", orderId).retrieve().body(ByteArray::class.java)).get("data").items().single()
        assertEquals("ht", simulated.get("locale").asString())
    }

    @Test
    fun `a decline declared under the derived order id fails the payment with the operator detail`() {
        val reference = "INV-${UUID.randomUUID()}"
        val declared =
            simulator
                .put()
                .uri("/control/operators/mock_openfsp/orders/{orderId}/scenario", orderIds.derive(reference))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"scenario":"DECLINE"}""")
                .retrieve()
                .toBodilessEntity()
        assertEquals(204, declared.statusCode.value())

        val failed = awaitStatus(create(reference).get("id").asString(), "failed")
        assertEquals("declined", failed.get("failure_reason").asString())
        assertEquals("DECLINED", failed.get("failure_detail").get("code").asString())
    }

    companion object {
        private val GATEWAY_PORT = freePort()
        private val SIMULATOR_PORT = freePort()
        private val process = startSimulator()

        private val simulator: RestClient =
            RestClient
                .builder()
                .requestFactory(JdkClientHttpRequestFactory())
                .baseUrl("http://127.0.0.1:$SIMULATOR_PORT")
                .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
                .build()

        private fun freePort(): Int = ServerSocket(0).use { it.localPort }

        private fun startSimulator(): Process {
            val jar = File(System.getProperty("openfsp.simulator-jar") ?: error("the simulator jar is not provided"))
            val java = ProcessHandle.current().info().command().orElse("java")
            val started =
                ProcessBuilder(
                    java,
                    "-jar",
                    jar.absolutePath,
                    "--server.port=$SIMULATOR_PORT",
                    "--server.address=127.0.0.1",
                    "--simulator.public-base-url=http://127.0.0.1:$SIMULATOR_PORT",
                    "--simulator.response-delay=0ms",
                    "--simulator.settle-after=500ms",
                ).redirectErrorStream(true)
                    .redirectOutput(File("build/simulator-end-to-end.log"))
                    .start()
            val probe = RestClient.builder().requestFactory(JdkClientHttpRequestFactory()).build()
            repeat(STARTUP_ATTEMPTS) {
                val ready = runCatching { probe.get().uri("http://127.0.0.1:$SIMULATOR_PORT/mock_openfsp/.well-known/openfsp-configuration").retrieve().toBodilessEntity() }.isSuccess
                if (ready) return started
                check(started.isAlive) { "the simulator exited, see build/simulator-end-to-end.log" }
                Thread.sleep(STARTUP_POLL_MILLIS)
            }
            started.destroyForcibly()
            error("the simulator did not start")
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("server.port") { GATEWAY_PORT }
            registry.add("openfsp.public-base-url") { "http://127.0.0.1:$GATEWAY_PORT" }
            registry.add("openfsp.providers.mock_openfsp.base-url") { "http://127.0.0.1:$SIMULATOR_PORT/mock_openfsp" }
            registry.add("openfsp.reconciliation-interval") { "1h" }
            registry.add("openfsp.statement-interval") { "1h" }
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            process.destroy()
        }

        private const val STARTUP_ATTEMPTS = 300
        private const val STARTUP_POLL_MILLIS = 100L
    }
}
