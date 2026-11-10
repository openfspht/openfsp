package org.openfsp.gateway.providers.simulated

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.providers.DecimalAmounts
import org.openfsp.gateway.providers.ProviderFactory
import org.openfsp.gateway.providers.ProviderHttpFactory
import org.openfsp.gateway.providers.ProviderJson
import org.openfsp.gateway.providers.ProviderProperties
import org.openfsp.gateway.providers.SecretRedaction
import org.springframework.stereotype.Component

@Component
class SimulatedProviderFactory(
    private val http: ProviderHttpFactory,
    private val json: ProviderJson,
    private val amounts: DecimalAmounts,
    private val calendar: BusinessCalendar,
    private val catalog: SimulatedOperatorCatalog,
) : ProviderFactory {
    override val type = ProviderProperties.Provider.SIMULATED

    override fun create(
        id: String,
        settings: ProviderProperties.Provider,
        limits: AmountLimits,
    ): ProviderOperations {
        val simulated = SimulatedSettings.from(id, settings)
        val api = SimulatedApi(http.create(simulated.baseUrl, bearer = simulated.apiKey), json, SecretRedaction(simulated.secrets))
        return SimulatedProvider(id, catalog.descriptorOf(id), limits, api, SimulatedMapper(amounts, calendar), SimulatedCallbacks(json, simulated.callbackSecret))
    }
}
