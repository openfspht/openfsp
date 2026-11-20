package org.openfsp.protocol.idempotency

import org.openfsp.protocol.Digests
import org.openfsp.protocol.json.JsonValue
import org.openfsp.protocol.json.JsonValue.JsonArray
import org.openfsp.protocol.json.JsonValue.JsonBoolean
import org.openfsp.protocol.json.JsonValue.JsonNull
import org.openfsp.protocol.json.JsonValue.JsonNumber
import org.openfsp.protocol.json.JsonValue.JsonObject
import org.openfsp.protocol.json.JsonValue.JsonString
import java.math.BigDecimal
import java.text.Normalizer

class RequestFingerprints(
    private val digests: Digests,
) {
    fun of(body: JsonValue?): String = digests.sha256Hex(canonical(body))

    fun canonical(body: JsonValue?): String = buildString { write(body ?: JsonObject(emptyMap()), normalized = true, root = true) }

    private fun StringBuilder.write(
        value: JsonValue,
        normalized: Boolean,
        root: Boolean = false,
    ) {
        when (value) {
            is JsonObject -> members(value, normalized, root)
            is JsonArray -> {
                append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) append(',')
                    write(item, normalized)
                }
                append(']')
            }
            is JsonString -> string(text(value.value, normalized))
            is JsonNumber -> append(number(value.value))
            is JsonBoolean -> append(value.value)
            JsonNull -> append("null")
        }
    }

    private fun StringBuilder.members(
        value: JsonObject,
        normalized: Boolean,
        root: Boolean,
    ) {
        append('{')
        value.members
            .filterValues { it != JsonNull }
            .map { (name, member) -> Member(name, text(name, normalized), member) }
            .sortedWith(compareBy<Member>({ it.canonicalName }, { it.name }))
            .forEachIndexed { index, member ->
                if (index > 0) append(',')
                string(member.canonicalName)
                append(':')
                write(member.value, normalized && !(root && member.name == METADATA))
            }
        append('}')
    }

    private data class Member(
        val name: String,
        val canonicalName: String,
        val value: JsonValue,
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

    private fun StringBuilder.string(value: String) {
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

    private companion object {
        const val METADATA = "metadata"
        const val PLAIN_DIGITS = 21
    }
}
