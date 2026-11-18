package org.openfsp.gateway.idempotency

import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.security.MessageDigest
import java.text.Normalizer
import java.util.HexFormat

@Component
class RequestFingerprints(
    private val json: JsonMapper,
) {
    fun of(body: ByteArray): RequestFingerprint {
        val tree = if (body.isEmpty()) json.createObjectNode() else json.readTree(body)
        return RequestFingerprint(digest(canonical(tree, normalized = true).toByteArray(Charsets.UTF_8)), digest(json.writeValueAsBytes(legacy(tree))))
    }

    private fun digest(bytes: ByteArray): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun canonical(
        node: JsonNode,
        normalized: Boolean,
    ): String =
        when {
            node.isObject ->
                node
                    .propertyNames()
                    .filterNot { node.get(it).isNull }
                    .sorted()
                    .joinToString(",", "{", "}") { name -> string(name) + ":" + canonical(node.get(name), normalized && name != METADATA) }
            node.isArray -> node.joinToString(",", "[", "]") { canonical(it, normalized) }
            node.isString -> string(if (normalized) Normalizer.normalize(node.asString(), Normalizer.Form.NFC) else node.asString())
            node.isIntegralNumber -> node.bigIntegerValue().toString()
            else -> json.writeValueAsString(node)
        }

    private fun string(value: String): String =
        buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
                }
            }
            append('"')
        }

    private fun legacy(node: JsonNode): JsonNode =
        when {
            node is ObjectNode -> json.createObjectNode().also { sorted -> node.propertyNames().sorted().forEach { sorted.set(it, legacy(node.get(it))) } }
            node.isArray -> json.createArrayNode().also { array -> node.forEach { array.add(legacy(it)) } }
            else -> node
        }

    private companion object {
        const val METADATA = "metadata"
    }
}
