package org.openfsp.gateway.providers.natcash

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
class NatCashProviderFactory(
    private val http: ProviderHttpFactory,
    private val json: ProviderJson,
    private val amounts: DecimalAmounts,
    private val calendar: BusinessCalendar,
) : ProviderFactory {
    override val type = TYPE

    override fun create(setup: ProviderSetup): ProviderOperations {
        val natCash = NatCashSettings.from(setup.settings)
        val signatures = NatCashSignatures(natCash)
        val api = NatCashApi(http.create(natCash.paymentBase), http.create(natCash.queryBase), json, SecretRedaction(natCash.secrets))
        val descriptor = NatCashProvider.DESCRIPTOR.copy(displayName = setup.settings.displayName ?: NatCashProvider.DESCRIPTOR.displayName, logoUrl = setup.logoUrl)
        return NatCashProvider(setup.id, descriptor, setup.limits, api, NatCashMapper(natCash, signatures, amounts, calendar, json), signatures)
    }

    companion object {
        const val TYPE = "natcash"
    }
}
