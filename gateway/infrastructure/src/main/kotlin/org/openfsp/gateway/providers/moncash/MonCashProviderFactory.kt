package org.openfsp.gateway.providers.moncash

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
class MonCashProviderFactory(
    private val http: ProviderHttpFactory,
    private val json: ProviderJson,
    private val amounts: DecimalAmounts,
    private val calendar: BusinessCalendar,
) : ProviderFactory {
    override val type = TYPE

    override fun create(
        id: String,
        settings: ProviderProperties.Provider,
        limits: AmountLimits,
    ): ProviderOperations {
        val monCash = MonCashSettings.from(id, settings)
        val client = http.create(monCash.apiBase)
        val errors = MonCashErrors(json, SecretRedaction(listOf(monCash.clientId, monCash.clientSecret)))
        val tokens = MonCashTokens(client, json, calendar, monCash, errors)
        val api = MonCashApi(client, json, tokens, errors)
        return MonCashProvider(id, limits, api, MonCashMapper(amounts, calendar, monCash.checkoutBase))
    }

    companion object {
        const val TYPE = "moncash"
    }
}
