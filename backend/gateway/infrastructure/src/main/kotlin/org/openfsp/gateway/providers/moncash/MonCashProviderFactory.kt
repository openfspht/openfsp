package org.openfsp.gateway.providers.moncash

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.providers.DecimalAmounts
import org.openfsp.gateway.providers.ProviderFactory
import org.openfsp.gateway.providers.ProviderHttpFactory
import org.openfsp.gateway.providers.ProviderJson
import org.openfsp.gateway.providers.ProviderSetup
import org.openfsp.gateway.providers.SecretRedaction
import org.openfsp.gateway.providers.configuredBy
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

    override val descriptor = MonCashProvider.DESCRIPTOR

    override fun create(setup: ProviderSetup): ProviderOperations {
        val monCash = MonCashSettings.from(setup.id, setup.settings)
        val client = http.create(monCash.apiBase)
        val token = MonCashAccessToken(monCash, calendar)
        val redaction = SecretRedaction(listOf(monCash.clientId, monCash.clientSecret), token::secrets)
        val errors = MonCashErrors(json, redaction, calendar)
        val api = MonCashApi(client, json, token, errors)
        return MonCashProvider(setup.id, descriptor.configuredBy(setup), setup.limits, api, MonCashMapper(amounts, calendar, monCash.checkoutBase, redaction))
    }

    companion object {
        const val TYPE = "moncash"
    }
}
