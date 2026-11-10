package org.openfsp.gateway.providers.moncash

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.providers.DecimalAmounts
import org.openfsp.gateway.providers.ProviderFactory
import org.openfsp.gateway.providers.ProviderHttpFactory
import org.openfsp.gateway.providers.ProviderJson
import org.openfsp.gateway.providers.ProviderSetup
import org.openfsp.gateway.providers.SecretRedaction
import org.springframework.stereotype.Component

@Component
class MonCashProviderFactory(
    private val http: ProviderHttpFactory,
    private val json: ProviderJson,
    private val amounts: DecimalAmounts,
    private val calendar: BusinessCalendar,
) : ProviderFactory {
    override val type = TYPE

    override fun create(setup: ProviderSetup): ProviderOperations {
        val monCash = MonCashSettings.from(setup.id, setup.settings)
        val client = http.create(monCash.apiBase)
        val errors = MonCashErrors(json, SecretRedaction(listOf(monCash.clientId, monCash.clientSecret)))
        val tokens = MonCashTokens(client, json, calendar, monCash, errors)
        val api = MonCashApi(client, json, tokens, errors)
        val descriptor = MonCashProvider.DESCRIPTOR.copy(displayName = setup.settings.displayName ?: MonCashProvider.DESCRIPTOR.displayName, logoUrl = setup.logoUrl)
        return MonCashProvider(setup.id, descriptor, setup.limits, api, MonCashMapper(amounts, calendar, monCash.checkoutBase))
    }

    companion object {
        const val TYPE = "moncash"
    }
}
