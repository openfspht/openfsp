package org.openfsp.gateway

import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.net.ServerSocket

class SimulatorProcess(
    logName: String,
    settings: Map<String, String>,
) : AutoCloseable {
    val port: Int = freePort()
    val baseUrl: String = "http://127.0.0.1:$port"
    private val json = JsonMapper.builder().build()
    private val process = start(logName, settings)

    val client: RestClient =
        RestClient
            .builder()
            .requestFactory(JdkClientHttpRequestFactory())
            .baseUrl(baseUrl)
            .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
            .build()

    fun declare(
        operator: String,
        orderId: String,
        scenario: String,
    ): Int =
        client
            .put()
            .uri("/control/operators/{operator}/orders/{orderId}/scenario", operator, orderId)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"scenario":"$scenario"}""")
            .retrieve()
            .toBodilessEntity()
            .statusCode
            .value()

    fun journal(
        operator: String,
        contains: String,
    ): List<JsonNode> =
        read("/control/journal?operator={operator}&q={q}&size=$PAGE_SIZE", operator, contains)
            .get("data")
            .items()
            .sortedBy { it.get("received_at").asString() }

    fun payment(
        operator: String,
        orderId: String,
    ): JsonNode? = read("/control/payments?operator={operator}&q={q}", operator, orderId).get("data").items().singleOrNull { it.get("order_id").asString() == orderId }

    fun payments(
        operator: String,
        orderId: String,
    ): List<JsonNode> = read("/control/payments?operator={operator}&q={q}", operator, orderId).get("data").items().filter { it.get("order_id").asString() == orderId }

    fun deliverCallback(paymentId: String): Int =
        client
            .post()
            .uri("/control/payments/{id}/callback", paymentId)
            .retrieve()
            .toBodilessEntity()
            .statusCode
            .value()

    fun read(
        path: String,
        vararg variables: Any,
    ): JsonNode = json.readTree(client.get().uri(path, *variables).retrieve().body(ByteArray::class.java))

    override fun close() {
        process.destroy()
    }

    private fun start(
        logName: String,
        settings: Map<String, String>,
    ): Process {
        val jar = File(System.getProperty("openfsp.simulator-jar") ?: error("the simulator jar is not provided"))
        val java = ProcessHandle.current().info().command().orElse("java")
        val arguments =
            mapOf(
                "server.port" to port.toString(),
                "server.address" to "127.0.0.1",
                "openfsp.simulator.public-base-url" to baseUrl,
                "openfsp.simulator.response-delay" to "0ms",
            ) + settings
        val log = File("build/$logName.log")
        val started =
            ProcessBuilder(listOf(java, "-jar", jar.absolutePath) + arguments.map { (name, value) -> "--$name=$value" })
                .redirectErrorStream(true)
                .redirectOutput(log)
                .start()
        val probe = RestClient.builder().requestFactory(JdkClientHttpRequestFactory()).build()
        repeat(STARTUP_ATTEMPTS) {
            val ready = runCatching { probe.get().uri("$baseUrl/mock_openfsp/.well-known/openfsp-configuration").retrieve().toBodilessEntity() }.isSuccess
            if (ready) return started
            check(started.isAlive) { "the simulator exited, see ${log.path}" }
            Thread.sleep(STARTUP_POLL_MILLIS)
        }
        started.destroyForcibly()
        error("the simulator did not start")
    }

    companion object {
        private const val STARTUP_ATTEMPTS = 300
        private const val STARTUP_POLL_MILLIS = 100L
        private const val PAGE_SIZE = 100

        fun freePort(): Int = ServerSocket(0).use { it.localPort }

        fun JsonNode.items(): List<JsonNode> = (0 until size()).map { get(it) }
    }
}
