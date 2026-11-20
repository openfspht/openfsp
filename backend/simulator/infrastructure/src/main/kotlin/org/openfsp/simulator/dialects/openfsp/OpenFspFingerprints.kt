package org.openfsp.simulator.dialects.openfsp

import org.openfsp.server.idempotency.RequestFingerprints
import org.openfsp.server.json.JsonValue
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import java.math.BigDecimal

@Component
class OpenFspFingerprints {
    fun of(tree: JsonNode?): String = RequestFingerprints.of(tree?.let(::valueOf))

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
