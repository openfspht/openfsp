package org.openfsp.gateway.providers.natcash

import org.openfsp.gateway.providers.ProviderProperties
import java.net.URI

data class NatCashSettings(
    val paymentBase: URI,
    val queryBase: URI,
    val username: String,
    val password: String,
    val partnerCode: String,
    val privateKey: String,
    val functionCode: String,
) {
    val secrets: List<String> get() = listOf(username, password, partnerCode, privateKey, functionCode)

    companion object {
        const val USERNAME = "username"
        const val PASSWORD = "password"
        const val PARTNER_CODE = "partner-code"
        const val PRIVATE_KEY = "private-key"
        const val FUNCTION_CODE = "function-code"

        fun from(provider: ProviderProperties.Provider) =
            NatCashSettings(
                paymentBase = provider.baseUrl,
                queryBase = provider.secondaryUrl ?: provider.baseUrl,
                username = provider.credential(USERNAME),
                password = provider.credential(PASSWORD),
                partnerCode = provider.credential(PARTNER_CODE),
                privateKey = provider.credential(PRIVATE_KEY),
                functionCode = provider.credential(FUNCTION_CODE),
            )
    }
}
