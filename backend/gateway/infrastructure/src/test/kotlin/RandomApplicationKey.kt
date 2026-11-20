package org.openfsp.gateway

import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.MapPropertySource
import java.security.SecureRandom
import java.util.Base64

class RandomApplicationKey : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(context: ConfigurableApplicationContext) {
        val key = Base64.getEncoder().encodeToString(ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes))
        context.environment.propertySources.addLast(MapPropertySource(SOURCE, mapOf(PROPERTY to key)))
    }

    private companion object {
        const val SOURCE = "randomApplicationKey"
        const val PROPERTY = "openfsp.app-key"
        const val KEY_BYTES = 32
    }
}
