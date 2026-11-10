package org.openfsp.gateway.providers.moncash

import org.openfsp.gateway.providers.ProviderProperties
import java.net.URI

data class MonCashSettings(
    val apiBase: URI,
    val checkoutBase: URI,
    val clientId: String,
    val clientSecret: String,
) {
    companion object {
        const val CLIENT_ID = "client-id"
        const val CLIENT_SECRET = "client-secret"

        fun from(
            id: String,
            provider: ProviderProperties.Provider,
        ) = MonCashSettings(
            apiBase = provider.baseUrl,
            checkoutBase = provider.secondaryUrl ?: error("$id needs secondary-url, the MonCash middleware base"),
            clientId = provider.credential(CLIENT_ID),
            clientSecret = provider.credential(CLIENT_SECRET),
        )
    }
}
