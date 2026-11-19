package org.openfsp.gateway.provider.spi

import org.openfsp.gateway.provider.CredentialRevision
import java.time.Instant

interface AccountCredentialStore {
    fun rotate(
        revision: CredentialRevision,
        credentials: Map<String, String>,
    )

    fun retireActive(
        account: String,
        provider: String,
        at: Instant,
    ): Boolean

    fun revoke(
        account: String,
        provider: String,
        revision: String,
        at: Instant,
    ): Boolean

    fun revisionsOf(account: String): List<CredentialRevision>

    fun purgeRetired(
        retiredBefore: Instant,
        batch: Int,
    ): Int
}
