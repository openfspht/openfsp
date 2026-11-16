package org.openfsp.sdk.protocol

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

internal object Json {
    val mapper: JsonMapper =
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build()

    fun write(value: Any): ByteArray = mapper.writeValueAsBytes(value)

    fun <T> read(
        bytes: ByteArray,
        type: Class<T>,
    ): T? = runCatching { mapper.readValue(bytes, type) }.getOrNull()

    fun tree(bytes: ByteArray): JsonNode? = runCatching { mapper.readTree(bytes) }.getOrNull()?.takeIf { it.isObject }
}
