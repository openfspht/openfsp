package org.openfsp.gateway.providers

import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.spi.Providers
import org.openfsp.protocol.Money
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ProviderConfiguration {
    @Bean
    fun providers(
        properties: ProviderProperties,
        factories: List<ProviderFactory>,
    ): Providers {
        val byType = factories.associateBy { it.type }
        return ConfiguredProviders(
            properties.providers.map { (id, settings) ->
                val factory = byType[settings.type] ?: error("No adapter of type ${settings.type} for provider $id")
                factory.create(id, settings, settings.limits.toDomain())
            },
        )
    }

    private fun ProviderProperties.Limits.toDomain() = AmountLimits(minAmount?.let(Money::htg), maxAmount?.let(Money::htg), amountStep?.let(Money::htg))
}
