package org.openfsp.simulator.dialects.openfsp

import org.openfsp.simulator.operator.space.SpaceId
import org.openfsp.simulator.operator.space.spi.SpaceCleanup
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
) : SpaceCleanup {
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

    private val entries = ConcurrentHashMap<Triple<SpaceId, String, String>, Entry>()

    fun fingerprint(tree: JsonNode): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(canonical(tree))))

    fun start(
        space: SpaceId,
        operator: String,
        key: String,
        fingerprint: String,
        stillValid: (RecordedResponse) -> Boolean,
    ): IdempotentStart {
        var start: IdempotentStart = IdempotentStart.Fresh
        entries.compute(Triple(space, operator, key)) { _, entry ->
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
        space: SpaceId,
        operator: String,
        key: String,
        fingerprint: String,
        response: RecordedResponse,
    ) {
        entries[Triple(space, operator, key)] = Entry.Done(fingerprint, response)
    }

    fun abandon(
        space: SpaceId,
        operator: String,
        key: String,
    ) {
        entries.remove(Triple(space, operator, key))
    }

    override fun clear(space: SpaceId) {
        entries.keys.removeIf { it.first == space }
    }

    private fun canonical(node: JsonNode): JsonNode =
        when {
            node is ObjectNode -> json.createObjectNode().also { sorted -> node.propertyNames().sorted().forEach { sorted.set(it, canonical(node.get(it))) } }
            node.isArray -> json.createArrayNode().also { array -> node.forEach { array.add(canonical(it)) } }
            else -> node
        }
}
