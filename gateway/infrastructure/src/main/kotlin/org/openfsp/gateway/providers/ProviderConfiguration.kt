package org.openfsp.gateway.providers

import org.openfsp.gateway.configuration.GatewayProperties
import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.spi.Providers
import org.openfsp.protocol.Money
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI

@Configuration(proxyBeanMethods = false)
class ProviderConfiguration {
    @Bean
    fun providers(
        properties: ProviderProperties,
        gateway: GatewayProperties,
        factories: List<ProviderFactory>,
    ): Providers {
        val byType = factories.associateBy { it.type }
        properties.providers.forEach { (id, settings) ->
            val unset = settings.unsetSecrets()
            check(unset.isEmpty()) { "provider $id has no value for ${unset.joinToString()}: set them in the environment or in OPENFSP_SECRETS_DIR" }
        }
        return ConfiguredProviders(
            properties.providers.map { (id, settings) ->
                val factory = byType[settings.type] ?: error("No adapter of type ${settings.type} for provider $id")
                factory.create(ProviderSetup(id, settings, settings.limits.toDomain(), settings.logoUrl?.let { logoOf(gateway.publicBaseUrl, it) }))
            },
        )
    }

    private fun logoOf(
        publicBaseUrl: URI,
        logo: String,
    ): URI = URI(logo).takeIf { it.isAbsolute } ?: publicBaseUrl.resolve(logo)

    private fun ProviderProperties.Limits.toDomain() = AmountLimits(minAmount?.let(Money::htg), maxAmount?.let(Money::htg), amountStep?.let(Money::htg))
}
