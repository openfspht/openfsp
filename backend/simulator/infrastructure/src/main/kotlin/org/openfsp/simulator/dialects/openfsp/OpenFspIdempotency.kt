package org.openfsp.simulator.dialects.openfsp

import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap

data class RecordedResponse(
    val status: Int,
    val body: Any,
    val paymentId: String?,
    val location: String? = null,
)

sealed interface IdempotentStart {
    data object Fresh : IdempotentStart

    data object InProgress : IdempotentStart

    data object Reused : IdempotentStart

    data class Replay(
        val response: RecordedResponse,
    ) : IdempotentStart
}

@Component
class OpenFspIdempotency(
    private val json: JsonMapper,
) {
    private sealed interface Entry {
        val fingerprint: String

        data class Running(
            override val fingerprint: String,
        ) : Entry

        data class Done(
            override val fingerprint: String,
            val response: RecordedResponse,
        ) : Entry
    }

    private val entries = ConcurrentHashMap<Pair<String, String>, Entry>()

    fun fingerprint(tree: JsonNode): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(canonical(tree))))

    fun start(
        operator: String,
        key: String,
        fingerprint: String,
        stillValid: (RecordedResponse) -> Boolean,
    ): IdempotentStart {
        var start: IdempotentStart = IdempotentStart.Fresh
        entries.compute(operator to key) { _, entry ->
            when {
                entry == null -> Entry.Running(fingerprint).also { start = IdempotentStart.Fresh }
                entry.fingerprint != fingerprint -> entry.also { start = IdempotentStart.Reused }
                entry is Entry.Running -> entry.also { start = IdempotentStart.InProgress }
                entry is Entry.Done && stillValid(entry.response) -> entry.also { start = IdempotentStart.Replay(entry.response) }
                else -> Entry.Running(fingerprint).also { start = IdempotentStart.Fresh }
            }
        }
        return start
    }

    fun finish(
        operator: String,
        key: String,
        fingerprint: String,
        response: RecordedResponse,
    ) {
        entries[operator to key] = Entry.Done(fingerprint, response)
    }

    fun abandon(
        operator: String,
        key: String,
    ) {
        entries.remove(operator to key)
    }

    fun clear() = entries.clear()

    private fun canonical(node: JsonNode): JsonNode =
        when {
            node is ObjectNode -> json.createObjectNode().also { sorted -> node.propertyNames().sorted().forEach { sorted.set(it, canonical(node.get(it))) } }
            node.isArray -> json.createArrayNode().also { array -> node.forEach { array.add(canonical(it)) } }
            else -> node
        }
}
