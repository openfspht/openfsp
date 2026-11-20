package org.openfsp.gateway.providers.natcash

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.providers.DecimalAmounts
import org.openfsp.gateway.providers.ProviderFactory
import org.openfsp.gateway.providers.ProviderHttpFactory
import org.openfsp.gateway.providers.ProviderJson
import org.openfsp.gateway.providers.ProviderSetup
import org.openfsp.gateway.providers.SecretRedaction
import org.openfsp.server.Digests
import org.springframework.stereotype.Component

@Component
class NatCashProviderFactory(
    private val http: ProviderHttpFactory,
    private val json: ProviderJson,
    private val amounts: DecimalAmounts,
    private val calendar: BusinessCalendar,
    private val digests: Digests,
) : ProviderFactory {
    override val type = TYPE

    override val accountCredentials =
        listOf(NatCashSettings.USERNAME, NatCashSettings.PASSWORD, NatCashSettings.PARTNER_CODE, NatCashSettings.PRIVATE_KEY, NatCashSettings.FUNCTION_CODE)

    override fun describe(setup: ProviderSetup): ProviderDescriptor =
        NatCashProvider.DESCRIPTOR.copy(displayName = setup.settings.displayName ?: NatCashProvider.DESCRIPTOR.displayName, logoUrl = setup.logoUrl)

    override fun create(setup: ProviderSetup): ProviderOperations {
        val natCash = NatCashSettings.from(setup.settings)
        val signatures = NatCashSignatures(natCash, digests)
        val redaction = SecretRedaction(natCash.secrets)
        val api = NatCashApi(http.create(natCash.paymentBase), http.create(natCash.queryBase), json, redaction, calendar)
        return NatCashProvider(setup.id, describe(setup), setup.limits, api, NatCashMapper(natCash, signatures, amounts, calendar, json, redaction), signatures)
    }

    companion object {
        const val TYPE = "natcash"
    }
}
