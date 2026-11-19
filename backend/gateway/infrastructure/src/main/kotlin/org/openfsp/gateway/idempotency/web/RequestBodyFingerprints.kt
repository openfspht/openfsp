package org.openfsp.gateway.idempotency.web

import org.openfsp.gateway.idempotency.RequestFingerprint
import org.openfsp.protocol.idempotency.RequestFingerprints
import org.openfsp.protocol.json.JsonValue
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal

@Component
class RequestBodyFingerprints(
    private val json: JsonMapper,
    private val fingerprints: RequestFingerprints,
) {
    fun of(body: ByteArray): RequestFingerprint = RequestFingerprint(fingerprints.of(if (body.isEmpty()) null else valueOf(json.readTree(body))))

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
