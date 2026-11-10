package org.openfsp.gateway.providers.simulated

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.providers.DecimalAmounts
import org.openfsp.gateway.providers.ProviderFactory
import org.openfsp.gateway.providers.ProviderHttpFactory
import org.openfsp.gateway.providers.ProviderJson
import org.openfsp.gateway.providers.ProviderProperties
import org.openfsp.gateway.providers.ProviderSetup
import org.openfsp.gateway.providers.SecretRedaction
import org.springframework.stereotype.Component

@Component
class SimulatedProviderFactory(
    private val http: ProviderHttpFactory,
    private val json: ProviderJson,
    private val amounts: DecimalAmounts,
    private val calendar: BusinessCalendar,
) : ProviderFactory {
    override val type = ProviderProperties.Provider.SIMULATED

    override fun create(setup: ProviderSetup): ProviderOperations {
        val simulated = SimulatedSettings.from(setup.id, setup.settings)
        val profile = setup.settings.profile ?: error("${setup.id} needs a profile describing the simulated operator")
        val api = SimulatedApi(http.create(simulated.baseUrl, bearer = simulated.apiKey), json, SecretRedaction(simulated.secrets))
        return SimulatedProvider(setup.id, descriptorOf(profile, setup), setup.limits, api, SimulatedMapper(amounts, calendar), SimulatedCallbacks(json, simulated.callbackSecret))
    }

    private fun descriptorOf(
        profile: ProviderProperties.SimulatedProfile,
        setup: ProviderSetup,
    ) = ProviderDescriptor(
        displayName = profile.displayName,
        capabilities = profile.capabilities,
        nextActions = profile.nextActions,
        payerRequired = profile.payerRequired,
        returnUrlRequired = profile.returnUrlRequired,
        idempotentCreation = profile.idempotentCreation,
        locales = profile.locales,
        logoUrl = setup.logoUrl,
    )
}
