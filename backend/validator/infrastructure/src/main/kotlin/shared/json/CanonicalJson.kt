package org.openfsp.validator.shared.json

import org.openfsp.server.Digests
import org.openfsp.server.appendJsonString
import tools.jackson.databind.JsonNode
import java.math.BigDecimal

object CanonicalJson {
    fun of(node: JsonNode): String = StringBuilder().also { write(node, it) }.toString()

    fun sha256(node: JsonNode): String = Digests.sha256Hex(of(node))

    private fun write(
        node: JsonNode,
        out: StringBuilder,
    ) {
        when {
            node.isObject -> {
                out.append('{')
                node.propertyNames().sorted().forEachIndexed { index, name ->
                    if (index > 0) out.append(',')
                    out.appendJsonString(name)
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
            node.isString -> out.appendJsonString(node.stringValue())
            node.isIntegralNumber -> out.append(node.bigIntegerValue().toString())
            node.isNumber -> out.append(number(node.doubleValue()))
            node.isBoolean -> out.append(node.booleanValue())
            node.isNull -> out.append("null")
            else -> throw IllegalArgumentException("${node.nodeType} has no canonical JSON form")
        }
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
