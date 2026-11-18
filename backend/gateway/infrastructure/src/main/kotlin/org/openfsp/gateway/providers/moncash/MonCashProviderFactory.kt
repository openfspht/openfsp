package org.openfsp.gateway.providers.moncash

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.ProviderDescriptor
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

    override val accountCredentials = listOf(MonCashSettings.CLIENT_ID, MonCashSettings.CLIENT_SECRET)

    override fun describe(setup: ProviderSetup): ProviderDescriptor =
        MonCashProvider.DESCRIPTOR.copy(displayName = setup.settings.displayName ?: MonCashProvider.DESCRIPTOR.displayName, logoUrl = setup.logoUrl)

    override fun create(setup: ProviderSetup): ProviderOperations {
        val monCash = MonCashSettings.from(setup.id, setup.settings)
        val client = http.create(monCash.apiBase)
        lateinit var tokens: MonCashTokens
        val redaction = SecretRedaction(listOf(monCash.clientId, monCash.clientSecret)) { tokens.secrets() }
        val errors = MonCashErrors(json, redaction, calendar)
        tokens = MonCashTokens(client, json, calendar, monCash, errors)
        val api = MonCashApi(client, json, tokens, errors)
        return MonCashProvider(setup.id, describe(setup), setup.limits, api, MonCashMapper(amounts, calendar, monCash.checkoutBase, redaction))
    }

    companion object {
        const val TYPE = "moncash"
    }
}
