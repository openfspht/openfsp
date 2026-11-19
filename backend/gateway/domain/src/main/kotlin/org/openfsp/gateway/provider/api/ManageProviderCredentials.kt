package org.openfsp.gateway.provider.api

import org.openfsp.gateway.provider.AccountProvider
import org.openfsp.gateway.provider.CredentialRevision

interface ManageProviderCredentials {
    fun providersOf(account: String): List<AccountProvider>

    fun save(
        account: String,
        provider: String,
        credentials: Map<String, String?>?,
    ): CredentialRevision

    fun remove(
        account: String,
        provider: String,
    )

    fun revoke(
        account: String,
        provider: String,
        revision: String,
    )

    fun purgeRetired(
        batch: Int,
        maxBatches: Int,
    ): Int
}
