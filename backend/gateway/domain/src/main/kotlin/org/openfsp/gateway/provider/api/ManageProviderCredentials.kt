package org.openfsp.gateway.provider.api

import org.openfsp.gateway.provider.AccountProvider

interface ManageProviderCredentials {
    fun providersOf(account: String): List<AccountProvider>

    fun save(
        account: String,
        provider: String,
        credentials: Map<String, String?>?,
    )

    fun remove(
        account: String,
        provider: String,
    )
}
