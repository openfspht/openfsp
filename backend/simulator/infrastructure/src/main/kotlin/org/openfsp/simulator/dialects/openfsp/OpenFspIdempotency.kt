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

data class IdempotencyScope(
    val space: SpaceId,
    val operator: String,
    val principal: String,
    val key: String,
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

    private val entries = ConcurrentHashMap<IdempotencyScope, Entry>()

    fun fingerprint(tree: JsonNode?): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(canonical(tree ?: json.createObjectNode()))))

    fun start(
        scope: IdempotencyScope,
        fingerprint: String,
        stillValid: (RecordedResponse) -> Boolean,
    ): IdempotentStart {
        var start: IdempotentStart = IdempotentStart.Fresh
        entries.compute(scope) { _, entry ->
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
        scope: IdempotencyScope,
        fingerprint: String,
        response: RecordedResponse,
    ) {
        entries[scope] = Entry.Done(fingerprint, response)
    }

    fun abandon(scope: IdempotencyScope) {
        entries.remove(scope)
    }

    override fun clear(space: SpaceId) {
        entries.keys.removeIf { it.space == space }
    }

    private fun canonical(node: JsonNode): JsonNode =
        when {
            node is ObjectNode -> json.createObjectNode().also { sorted -> node.propertyNames().sorted().forEach { sorted.set(it, canonical(node.get(it))) } }
            node.isArray -> json.createArrayNode().also { array -> node.forEach { array.add(canonical(it)) } }
            else -> node
        }
}
