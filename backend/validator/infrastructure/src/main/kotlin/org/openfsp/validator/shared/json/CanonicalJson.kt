package org.openfsp.validator.shared.json

import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.HexFormat

object CanonicalJson {
    fun of(node: JsonNode): String = StringBuilder().also { write(node, it) }.toString()

    fun sha256(node: JsonNode): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(of(node).toByteArray(Charsets.UTF_8)))

    private fun write(
        node: JsonNode,
        out: StringBuilder,
    ) {
        when {
            node.isObject -> {
                out.append('{')
                node.propertyNames().sorted().forEachIndexed { index, name ->
                    if (index > 0) out.append(',')
                    string(name, out)
                    out.append(':')
                    write(node.get(name), out)
                }
                out.append('}')
            }
            node.isArray -> {
                out.append('[')
                node.values().forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    write(item, out)
                }
                out.append(']')
            }
            node.isString -> string(node.stringValue(), out)
            node.isIntegralNumber -> out.append(node.bigIntegerValue().toString())
            node.isNumber -> out.append(number(node.doubleValue()))
            node.isBoolean -> out.append(node.booleanValue())
            node.isNull -> out.append("null")
            else -> throw IllegalArgumentException("${node.nodeType} has no canonical JSON form")
        }
    }

    private fun string(
        value: String,
        out: StringBuilder,
    ) {
        out.append('"')
        value.forEach { char ->
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\u000c' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (char < ' ') out.append("\\u%04x".format(char.code)) else out.append(char)
            }
        }
        out.append('"')
    }

    private fun number(value: Double): String {
        require(value.isFinite()) { "JSON has no form for $value" }
        if (value == 0.0) return "0"
        val decimal = BigDecimal(value.toString()).stripTrailingZeros()
        val magnitude = decimal.abs()
        if (magnitude >= SMALLEST_PLAIN && magnitude < LARGEST_PLAIN) return decimal.toPlainString()
        val digits = decimal.unscaledValue().abs().toString()
        val exponent = digits.length - 1 - decimal.scale()
        val mantissa = if (digits.length == 1) digits else "${digits[0]}.${digits.substring(1)}"
        return "${if (decimal.signum() < 0) "-" else ""}${mantissa}e${if (exponent >= 0) "+" else "-"}${kotlin.math.abs(exponent)}"
    }

    private val SMALLEST_PLAIN = BigDecimal("0.000001")
    private val LARGEST_PLAIN = BigDecimal("1e21")
}
