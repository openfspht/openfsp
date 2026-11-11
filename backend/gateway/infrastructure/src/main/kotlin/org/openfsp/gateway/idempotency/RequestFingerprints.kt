package org.openfsp.gateway.idempotency

import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.security.MessageDigest
import java.util.HexFormat

@Component
class RequestFingerprints(
    private val json: JsonMapper,
) {
    fun of(body: ByteArray): String {
        val tree = if (body.isEmpty()) json.createObjectNode() else json.readTree(body)
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(canonical(tree))))
    }

    private fun canonical(node: JsonNode): JsonNode =
        when {
            node is ObjectNode -> json.createObjectNode().also { sorted -> node.propertyNames().sorted().forEach { sorted.set(it, canonical(node.get(it))) } }
            node.isArray -> json.createArrayNode().also { array -> node.forEach { array.add(canonical(it)) } }
            else -> node
        }
}
