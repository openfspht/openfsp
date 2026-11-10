package org.openfsp.gateway.providers

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.provider.spi.Providers
import org.openfsp.protocol.Money
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper

@Configuration(proxyBeanMethods = false)
class ProviderConfiguration {
    @Bean
    fun providers(
        properties: ProviderProperties,
        json: JsonMapper,
        amounts: DecimalAmounts,
        calendar: BusinessCalendar,
    ): Providers {
        val lenient = json.rebuild().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()
        val operations =
            properties.providers.map { (id, settings) ->
                val descriptor = SimulatedOperators.descriptors[id] ?: error("No adapter for provider $id")
                SimulatedOperatorClient(id, descriptor, settings.limits.toDomain(), settings, properties.providerTimeout, lenient, amounts, calendar)
            }
        return ConfiguredProviders(operations)
    }

    private fun ProviderProperties.Limits.toDomain() =
        AmountLimits(minAmount?.let(Money::htg), maxAmount?.let(Money::htg), amountStep?.let(Money::htg))
}

class ConfiguredProviders(
    operations: List<ProviderOperations>,
) : Providers {
    private val byId = operations.associateBy { it.id }

    override fun get(id: String): ProviderOperations? = byId[id]

    override fun all(): List<ProviderOperations> = byId.values.sortedBy { it.id }
}
