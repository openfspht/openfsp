package org.openfsp.simulator.dialects.openfsp

import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.security.MessageDigest
import java.text.Normalizer
import java.util.HexFormat

@Component
class OpenFspFingerprints(
    private val json: JsonMapper,
) {
    fun of(tree: JsonNode?): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(canonical(tree ?: json.createObjectNode()))))

    private fun canonical(node: JsonNode): JsonNode =
        when {
            node is ObjectNode ->
                json.createObjectNode().also { sorted ->
                    node.propertyNames().filterNot { node.get(it).isNull }.map { it to nfc(it) }.sortedBy { it.second }.forEach { (name, normal) -> sorted.set(normal, canonical(node.get(name))) }
                }
            node.isArray -> json.createArrayNode().also { array -> node.forEach { array.add(canonical(it)) } }
            node.isString -> json.nodeFactory.stringNode(nfc(node.stringValue()))
            else -> node
        }

    private fun nfc(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC)
}
