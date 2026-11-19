package org.openfsp.simulator.dialects.openfsp

import org.openfsp.protocol.idempotency.RequestFingerprints
import org.openfsp.protocol.json.JsonValue
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import java.math.BigDecimal

@Component
class OpenFspFingerprints(
    private val fingerprints: RequestFingerprints,
) {
    fun of(tree: JsonNode?): String = fingerprints.of(tree?.let(::valueOf))

    private fun valueOf(node: JsonNode): JsonValue =
        when {
            node.isObject -> JsonValue.JsonObject(node.propertyNames().associateWith { valueOf(node.get(it)) })
            node.isArray -> JsonValue.JsonArray(node.values().map(::valueOf))
            node.isString -> JsonValue.JsonString(node.stringValue())
            node.isIntegralNumber -> JsonValue.JsonNumber(BigDecimal(node.bigIntegerValue()))
            node.isNumber -> JsonValue.JsonNumber(node.decimalValue())
            node.isBoolean -> JsonValue.JsonBoolean(node.booleanValue())
            else -> JsonValue.JsonNull
        }
}
