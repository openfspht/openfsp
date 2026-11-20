package org.openfsp.server.idempotency

import org.openfsp.server.Digests
import org.openfsp.server.appendJsonString
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.text.Normalizer

object RequestFingerprints {
    private const val METADATA = "metadata"
    private const val PLAIN_DIGITS = 21

    fun of(body: JsonNode?): String = Digests.sha256Hex(canonical(body))

    fun canonical(body: JsonNode?): String =
        buildString {
            if (body == null) append("{}") else write(body, normalized = true, root = true)
        }

    private fun StringBuilder.write(
        node: JsonNode,
        normalized: Boolean,
        root: Boolean = false,
    ) {
        when {
            node.isObject -> members(node, normalized, root)
            node.isArray -> {
                append('[')
                node.values().forEachIndexed { index, item ->
                    if (index > 0) append(',')
                    write(item, normalized)
                }
                append(']')
            }
            node.isString -> appendJsonString(text(node.stringValue(), normalized))
            node.isIntegralNumber -> append(number(BigDecimal(node.bigIntegerValue())))
            node.isNumber -> append(number(node.decimalValue()))
            node.isBoolean -> append(node.booleanValue())
            else -> append("null")
        }
    }

    private fun StringBuilder.members(
        node: JsonNode,
        normalized: Boolean,
        root: Boolean,
    ) {
        append('{')
        node
            .properties()
            .filterNot { (_, member) -> member.isNullValue() }
            .map { (name, member) -> Member(name, text(name, normalized), member) }
            .sortedWith(compareBy<Member>({ it.canonicalName }, { it.name }))
            .forEachIndexed { index, member ->
                if (index > 0) append(',')
                appendJsonString(member.canonicalName)
                append(':')
                write(member.value, normalized && !(root && member.name == METADATA))
            }
        append('}')
    }

    private fun JsonNode.isNullValue(): Boolean = !(isObject || isArray || isString || isNumber || isBoolean)

    private data class Member(
        val name: String,
        val canonicalName: String,
        val value: JsonNode,
    )

    private fun text(
        value: String,
        normalized: Boolean,
    ): String = if (normalized) Normalizer.normalize(value, Normalizer.Form.NFC) else value

    private fun number(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        val integral = stripped.scale() <= 0 && stripped.precision() - stripped.scale() <= PLAIN_DIGITS
        return if (integral) stripped.toBigIntegerExact().toString() else stripped.toString()
    }
}
