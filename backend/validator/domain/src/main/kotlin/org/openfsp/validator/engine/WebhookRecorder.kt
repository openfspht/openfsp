package org.openfsp.validator.engine

import org.openfsp.validator.engine.spi.WebhookInbox
import org.openfsp.validator.json.Json
import org.openfsp.validator.json.spi.JsonCodec
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant

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

class WebhookRecorder(
    override val url: String,
    private val json: JsonCodec,
    private val pointers: Pointers,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val budget: Long = DEFAULT_BUDGET,
    private val clock: Clock = Clock.systemUTC(),
) : WebhookInbox {
    private val recorded = mutableListOf<Delivery>()
    private val instructions = mutableListOf<Instruction>()
    private var recordedBytes = 0L
    private var refusals = 0

    private class Instruction(
        val where: Map<String, Json>,
        val reply: Reply,
        var remaining: Int?,
    )

    override fun deliveries(): List<Delivery> = synchronized(this) { recorded.toList() }

    override fun refused(): Int = synchronized(this) { refusals }

    override fun instruct(
        where: Map<String, Json>,
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
        synchronized(this) {
            if (recorded.size >= capacity || recordedBytes + body.size > budget) {
                refusals++
                return FULL
            }
        }
        val document = json.parse(body)
        synchronized(this) {
            val instruction = instructions.firstOrNull { (it.remaining ?: 1) > 0 && document != null && matches(it.where, document) }
            instruction?.let { it.remaining = it.remaining?.minus(1) }
            val reply = instruction?.reply ?: ACCEPTED
            recorded += Delivery(method.uppercase(), targetUri, headers, body, clock.instant(), reply.status)
            recordedBytes += body.size
            return reply
        }
    }

    private fun matches(
        where: Map<String, Json>,
        document: Json,
    ): Boolean = where.all { (pointer, value) -> pointers.resolve(document, pointer).let { it.isNotEmpty() && it.all { located -> located.node == value } } }

    companion object {
        const val DEFAULT_CAPACITY = 2000
        const val DEFAULT_BUDGET = 64L shl 20
        val ACCEPTED = Reply(204)
        val FULL = Reply(503)
    }
}
