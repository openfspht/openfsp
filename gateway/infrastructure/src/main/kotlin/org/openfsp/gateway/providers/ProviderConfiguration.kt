package org.openfsp.gateway.providers

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.provider.spi.Providers
import org.openfsp.gateway.providers.moncash.MonCashClient
import org.openfsp.gateway.providers.natcash.NatCashClient
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
                val limits = settings.limits.toDomain()
                when (settings.type) {
                    MonCashClient.TYPE -> MonCashClient(id, limits, settings, properties.providerTimeout, lenient, amounts, calendar)
                    NatCashClient.TYPE -> NatCashClient(id, limits, settings, properties.providerTimeout, lenient, amounts, calendar)
                    ProviderProperties.Provider.SIMULATED -> {
                        val descriptor = SimulatedOperators.descriptors[id] ?: error("No simulated operator named $id")
                        SimulatedOperatorClient(id, descriptor, limits, settings, properties.providerTimeout, lenient, amounts, calendar)
                    }
                    else -> error("No adapter of type ${settings.type} for provider $id")
                }
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
