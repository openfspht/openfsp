package org.openfsp.server

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.databind.node.StringNode

object ExchangeRedaction {
    const val MASK = "[REDACTED]"
    private val SECRET_HEADER = Regex("(?i)(password|secret|token|signature|private[_-]?key|api[_-]?key|authorization|cookie|credential)")
    private val SECRET_FIELD = Regex("(?i)(password|secret|token|signature|key|authorization|cookie|credential)")
    private const val BYTE_ORDER_MARK = "\uFEFF"
    private val FORM = Regex("^[^\\s{}\\[\\]\"]+$")
    private val PAIR = Regex("(^|[?&])([^=&]+)=([^&]*)")
    private val JSON = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build()

    fun isSecretName(name: String): Boolean = SECRET_FIELD.containsMatchIn(name)

    fun headers(headers: Map<String, String>): Map<String, String> = headers.mapValues { (name, value) -> if (SECRET_HEADER.containsMatchIn(name)) MASK else value }

    fun body(body: String): String {
        val text = body.removePrefix(BYTE_ORDER_MARK)
        if (text.isBlank()) return body
        runCatching { JSON.readTree(text) }.getOrNull()?.takeIf { it.isContainer }?.let { return JSON.writeValueAsString(masked(it)) }
        if (text.trimStart().let { it.startsWith("{") || it.startsWith("[") }) return MASK
        if (FORM.matches(text.trim())) return pairs(text)
        return if (SECRET_FIELD.containsMatchIn(text)) MASK else body
    }

    fun pairs(text: String): String = PAIR.replace(text) { match -> if (SECRET_FIELD.containsMatchIn(match.groupValues[2])) "${match.groupValues[1]}${match.groupValues[2]}=$MASK" else match.value }

    private fun masked(node: JsonNode): JsonNode {
        when (node) {
            is ObjectNode -> node.propertyNames().toList().forEach { name -> node.set(name, if (SECRET_FIELD.containsMatchIn(name)) StringNode.valueOf(MASK) else masked(node.get(name))) }
            is ArrayNode -> (0 until node.size()).forEach { index -> node.set(index, masked(node.get(index))) }
            else -> Unit
        }
        return node
    }
}
