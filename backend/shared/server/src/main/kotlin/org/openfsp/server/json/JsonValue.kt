package org.openfsp.server.json

import java.math.BigDecimal

sealed interface JsonValue {
    data class JsonObject(
        val members: Map<String, JsonValue>,
    ) : JsonValue

    data class JsonArray(
        val items: List<JsonValue>,
    ) : JsonValue

    data class JsonString(
        val value: String,
    ) : JsonValue

    data class JsonNumber(
        val value: BigDecimal,
    ) : JsonValue

    data class JsonBoolean(
        val value: Boolean,
    ) : JsonValue

    data object JsonNull : JsonValue
}
