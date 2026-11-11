package org.openfsp.gateway.providers

import org.openfsp.gateway.provider.ProviderFailure
import org.springframework.stereotype.Component
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

@Component
class ProviderJson {
    private val mapper =
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build()

    fun write(value: Any): ByteArray = mapper.writeValueAsBytes(value)

    fun <T : Any> read(
        body: ByteArray,
        type: Class<T>,
        provider: String,
    ): T = readOrNull(body, type) ?: throw ProviderFailure.Rejected("Unreadable response from $provider")

    fun <T : Any> readOrNull(
        body: ByteArray,
        type: Class<T>,
    ): T? = runCatching { mapper.readValue(body, type) }.getOrNull()
}
