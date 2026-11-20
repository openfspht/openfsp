package org.openfsp.gateway.providers.natcash

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
class NatCashProviderFactory(
    private val http: ProviderHttpFactory,
    private val json: ProviderJson,
    private val amounts: DecimalAmounts,
    private val calendar: BusinessCalendar,
) : ProviderFactory {
    override val type = TYPE

    override val accountCredentials =
        listOf(NatCashSettings.USERNAME, NatCashSettings.PASSWORD, NatCashSettings.PARTNER_CODE, NatCashSettings.PRIVATE_KEY, NatCashSettings.FUNCTION_CODE)

    override val descriptor = NatCashProvider.DESCRIPTOR

    override fun create(setup: ProviderSetup): ProviderOperations {
        val natCash = NatCashSettings.from(setup.settings)
        val signatures = NatCashSignatures(natCash)
        val redaction = SecretRedaction(natCash.secrets)
        val api = NatCashApi(http.create(natCash.paymentBase), http.create(natCash.queryBase), json, redaction, calendar)
        return NatCashProvider(setup.id, descriptor.configuredBy(setup), setup.limits, api, NatCashMapper(natCash, signatures, amounts, calendar, json, redaction), signatures)
    }

    companion object {
        const val TYPE = "natcash"
    }
}
