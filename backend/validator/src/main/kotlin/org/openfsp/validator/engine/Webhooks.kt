package org.openfsp.validator.engine

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class Delivery(
    val method: String,
    val targetUri: String,
    val headers: Map<String, List<String>>,
    val body: ByteArray,
    val receivedAt: Instant,
    val status: Int,
) {
    val path: String get() = URI.create(targetUri).rawPath ?: ""

    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

    fun exchange(): Exchange = Exchange(OutgoingRequest(method, targetUri, headers.mapValues { it.value.joinToString(", ") }, body), status, headers, body, receivedAt)
}

data class Reply(
    val status: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = ByteArray(0),
    val delay: Duration = Duration.ZERO,
)

interface WebhookInbox {
    val url: String

    fun deliveries(): List<Delivery>

    fun instruct(
        where: Map<String, JsonNode>,
        reply: Reply,
        times: Int?,
    )
}

class WebhookRecorder(
    override val url: String,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val clock: Clock = Clock.systemUTC(),
) : WebhookInbox {
    private val json: JsonMapper = JsonMapper.builder().build()
    private val recorded = mutableListOf<Delivery>()
    private val instructions = mutableListOf<Instruction>()

    private class Instruction(
        val where: Map<String, JsonNode>,
        val reply: Reply,
        var remaining: Int?,
    )

    override fun deliveries(): List<Delivery> = synchronized(this) { recorded.toList() }

    override fun instruct(
        where: Map<String, JsonNode>,
        reply: Reply,
        times: Int?,
    ) {
        synchronized(this) { instructions += Instruction(where, reply, times) }
    }

    fun receive(
        method: String,
        targetUri: String,
        headers: Map<String, List<String>>,
        body: ByteArray,
    ): Reply {
        val document = runCatching { json.readTree(body) }.getOrNull()?.takeUnless { it.isMissingNode }
        synchronized(this) {
            val instruction = instructions.firstOrNull { (it.remaining ?: 1) > 0 && document != null && matches(it.where, document) }
            instruction?.let { it.remaining = it.remaining?.minus(1) }
            val reply = instruction?.reply ?: ACCEPTED
            if (recorded.size < capacity) recorded += Delivery(method.uppercase(), targetUri, headers, body, clock.instant(), reply.status)
            return reply
        }
    }

    private fun matches(
        where: Map<String, JsonNode>,
        document: JsonNode,
    ): Boolean = where.all { (pointer, value) -> Pointers.resolve(document, pointer).let { it.isNotEmpty() && it.all { located -> located.node == value } } }

    companion object {
        const val DEFAULT_CAPACITY = 2000
        const val MAX_BODY_BYTES = 1 shl 20
        val ACCEPTED = Reply(204)

        fun readBounded(input: InputStream): ByteArray? {
            val bytes = input.readNBytes(MAX_BODY_BYTES + 1)
            return bytes.takeIf { it.size <= MAX_BODY_BYTES }
        }
    }
}

class LocalWebhookReceiver(
    bind: String,
    port: Int,
    publicUrl: String?,
) : AutoCloseable {
    private val executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()
    private val server: HttpServer = HttpServer.create(InetSocketAddress(bind, port), 0)
    private val origin: String
    val inbox: WebhookRecorder

    init {
        val listening = server.address.port
        val url = publicUrl ?: "http://${if (':' in bind) "[$bind]" else bind}:$listening$DEFAULT_PATH"
        val parsed = URI.create(url)
        origin = "${parsed.scheme}://${parsed.rawAuthority}"
        inbox = WebhookRecorder(url)
        server.executor = executor
        server.createContext("/", ::handle)
        server.start()
    }

    private fun handle(exchange: HttpExchange) {
        exchange.use {
            val body = WebhookRecorder.readBounded(exchange.requestBody)
            if (body == null) {
                exchange.sendResponseHeaders(PAYLOAD_TOO_LARGE, -1)
                return
            }
            val target = origin + exchange.requestURI.rawPath + (exchange.requestURI.rawQuery?.let { "?$it" } ?: "")
            val reply = inbox.receive(exchange.requestMethod, target, exchange.requestHeaders.toMap(), body)
            if (!reply.delay.isZero) Thread.sleep(reply.delay)
            reply.headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
            exchange.sendResponseHeaders(reply.status, if (reply.body.isEmpty()) -1 else reply.body.size.toLong())
            if (reply.body.isNotEmpty()) exchange.responseBody.write(reply.body)
        }
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }

    companion object {
        const val DEFAULT_PATH = "/webhooks"
        private const val PAYLOAD_TOO_LARGE = 413
    }
}
