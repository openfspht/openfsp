package org.openfsp.simulator.dialects.openfsp

import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.common.spi.SimulationCleanup
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap

data class RecordedResponse(
    val status: Int,
    val body: Any,
    val paymentId: String?,
    val location: String? = null,
)

data class IdempotencyScope(
    val operator: String,
    val account: String,
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
    private val clock: SimulationClock,
) : SimulationCleanup {
    private sealed interface Entry {
        val fingerprint: String

        data class Running(
            override val fingerprint: String,
        ) : Entry

        data class Done(
            override val fingerprint: String,
            val response: RecordedResponse,
            val recordedAt: Instant,
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
        entries[scope] = Entry.Done(fingerprint, response, clock.now())
    }

    fun abandon(scope: IdempotencyScope) {
        entries.remove(scope)
    }

    override fun purge(before: Instant) {
        entries.values.removeIf { it is Entry.Done && it.recordedAt.isBefore(before) }
    }

    override fun clear() {
        entries.clear()
    }

    override fun clear(account: String) {
        entries.keys.removeIf { it.account == account }
    }

    private fun canonical(node: JsonNode): JsonNode =
        when {
            node is ObjectNode -> json.createObjectNode().also { sorted -> node.propertyNames().sorted().forEach { sorted.set(it, canonical(node.get(it))) } }
            node.isArray -> json.createArrayNode().also { array -> node.forEach { array.add(canonical(it)) } }
            else -> node
        }
}
