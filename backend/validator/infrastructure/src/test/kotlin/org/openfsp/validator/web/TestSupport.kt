package org.openfsp.validator.web

import com.sun.net.httpserver.HttpServer
import org.springframework.boot.test.context.TestConfiguration
import tools.jackson.databind.JsonNode
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.HexFormat
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
}

class FakeSimulator : AutoCloseable {
    val requests = CopyOnWriteArrayList<String>()

    val authorizations = CopyOnWriteArrayList<String>()

    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val path = exchange.requestURI.path
                requests += "${exchange.requestMethod} $path"
                exchange.requestHeaders.getFirst("Authorization")?.let { authorizations += "$path $it" }
                val (status, body) =
                    when {
                        exchange.requestMethod == "POST" && path == "/runs" -> 201 to """{"space":"${HexFormat.of().formatHex(ByteArray(16).also(Random::nextBytes))}"}"""
                        path.startsWith("/runs/") -> 204 to ""
                        else -> 404 to """{"error":"not_found"}"""
                    }
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()
            start()
        }

    val url: String get() = "http://127.0.0.1:${server.address.port}"

    fun spaceRequests(space: String): List<String> = requests.filter { it.contains("/runs/$space/") }.map { it.replace("/runs/$space", "") }

    override fun close() = server.stop(0)
}

class FakeTarget : AutoCloseable {
    @Volatile
    var gate: CountDownLatch? = null

    val slowRequests = AtomicInteger()

    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                if (exchange.requestURI.path == "/v1/slow") {
                    slowRequests.incrementAndGet()
                    gate?.await(30, TimeUnit.SECONDS)
                }
                val (status, body) =
                    when (exchange.requestURI.path) {
                        "/.well-known/openfsp-configuration" -> 200 to """{"protocol_version":"0.1.0"}"""
                        "/v1/capabilities" -> 200 to """{"providers":[{"id":"mock_a","capabilities":["payments"]}]}"""
                        "/v1/things", "/v1/slow" -> 200 to """{"value":1,"echo":"${exchange.requestHeaders.getFirst("Authorization")}"}"""
                        else -> 404 to """{"type":"not-found"}"""
                    }
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()
            start()
        }

    val url: String get() = "http://127.0.0.1:${server.address.port}"

    override fun close() = server.stop(0)
}

object TestSuite {
    val directory: Path by lazy { write(Files.createTempDirectory("conformance")) }

    val concurrent: Path by lazy { writeConcurrent(Files.createTempDirectory("conformance")) }

    private fun catalogue(requirements: String) =
        """
        suite:
          version: 0.0.1
          protocol-version: 0.1.0
          discovery:
            path: /.well-known/openfsp-configuration
            protocol-version: /protocol_version
          capabilities:
            path: /v1/capabilities
            providers: /providers
            id: /id
            names: /capabilities
          journal-settle: 0ms
        limits:
          - reference: conformance#5.6
            text: signatures are not verified by this report
        requirements:
        $requirements
        """.trimIndent()

    private fun writeConcurrent(directory: Path): Path {
        directory.resolve("requirements.yaml").writeText(catalogue("  - { id: T-1, requirement: things#1, level: core, targets: [gateway, native-operator], title: one }"))
        val space = directory.resolve("scenarios/space").createDirectories()
        space.resolve("declare.yaml").writeText(
            """
            requirement: T-1
            title: drives its own simulator space while waiting on the target
            steps:
              - simulator: { default: { operator: mock_openfsp, scenario: DECLINE } }
              - request: { method: GET, path: /v1/slow }
                expect: { status: 200 }
            """.trimIndent(),
        )
        space.resolve("published.yaml").writeText(
            """
            requirement: T-1
            title: presents the revoked key the operator of the space publishes
            targets: [native-operator]
            assisted: { action: pass a revoked key, inputs: [revoked-key] }
            steps:
              - request: { method: GET, path: /v1/revoked, auth: "{{input.revoked-key}}" }
            """.trimIndent(),
        )
        return directory
    }

    private fun write(directory: Path): Path {
        directory.resolve("requirements.yaml").writeText(
            """
            suite:
              version: 0.0.1
              protocol-version: 0.1.0
              discovery:
                path: /.well-known/openfsp-configuration
                protocol-version: /protocol_version
              capabilities:
                path: /v1/capabilities
                providers: /providers
                id: /id
                names: /capabilities
              journal-settle: 0ms
            limits:
              - reference: conformance#5.6
                text: signatures are not verified by this report
            requirements:
              - { id: T-1, requirement: things#1, level: core, targets: [gateway], title: one }
              - { id: T-2, requirement: things#2, level: core, targets: [gateway], title: two }
            """.trimIndent(),
        )
        val things = directory.resolve("scenarios/things").createDirectories()
        things.resolve("pass.yaml").writeText(scenario("T-1", "/v1/things", "equals: { /value: 1 }"))
        things.resolve("fail.yaml").writeText(scenario("T-2", "/v1/things", "equals: { /value: 2 }"))
        things.resolve("restricted.yaml").writeText(
            "assisted: { action: provide a restricted key, inputs: [restricted-key] }\n" + scenario("T-1", "/v1/things", "status: 200", "\"{{input.restricted-key}}\""),
        )
        val slow = directory.resolve("scenarios/slow").createDirectories()
        slow.resolve("wait.yaml").writeText(scenario("T-1", "/v1/slow", "status: 200"))
        return directory
    }

    private fun scenario(
        requirement: String,
        path: String,
        expectation: String,
        auth: String? = null,
    ): String =
        """
        requirement: $requirement
        title: reads $path
        steps:
          - request: { method: GET, path: $path${auth?.let { ", auth: $it" } ?: ""} }
            expect: { $expectation }
        """.trimIndent()
}

fun JsonNode.items(): List<JsonNode> = (0 until size()).map(::get)

class Api(
    private val port: Int,
) {
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    fun get(
        path: String,
        accept: String = "application/json",
    ): HttpResponse<String> = client.send(HttpRequest.newBuilder(uri(path)).header("Accept", accept).GET().build(), HttpResponse.BodyHandlers.ofString())

    fun post(
        path: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse<String> =
        client.send(
            HttpRequest
                .newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .apply { headers.forEach(::header) }
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    fun events(path: String): List<Pair<String, String>> {
        val request = HttpRequest.newBuilder(uri(path)).header("Accept", "text/event-stream").timeout(Duration.ofSeconds(60)).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofLines())
        check(response.statusCode() == 200) { "events answered ${response.statusCode()}" }
        val events = mutableListOf<Pair<String, String>>()
        var name = "message"
        val data = StringBuilder()
        response.body().use { lines ->
            for (line in lines) {
                when {
                    line.startsWith("event:") -> name = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> data.append(line.removePrefix("data:"))
                    line.isEmpty() && data.isNotEmpty() -> {
                        events += name to data.toString()
                        data.clear()
                        name = "message"
                    }
                }
            }
        }
        return events
    }

    private fun uri(path: String) = URI.create("http://127.0.0.1:$port$path")
}

val TEST_SIMULATOR = org.openfsp.validator.web.configuration.SimulatorProperties(
    controlUrl = "http://127.0.0.1:8090",
    publicUrl = "http://127.0.0.1:8090",
)
