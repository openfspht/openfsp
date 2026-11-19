package org.openfsp.gateway.provider.spi

import org.openfsp.gateway.provider.StoredCredentials

interface AccountCredentialStore {
    fun save(
        account: String,
        provider: String,
        credentials: Map<String, String>,
    )

    fun remove(
        account: String,
        provider: String,
    ): Boolean

    fun of(account: String): List<StoredCredentials>
}
