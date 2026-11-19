package org.openfsp.gateway.provider.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.StoredCredentials
import org.openfsp.gateway.provider.spi.AccountCredentialStore
import java.time.Instant

@Stub
class InMemoryAccountCredentialStore(
    private val calendar: BusinessCalendar,
) : AccountCredentialStore {
    val saved = mutableMapOf<Pair<String, String>, Map<String, String>>()
    private val updatedAt = mutableMapOf<Pair<String, String>, Instant>()

    override fun save(
        account: String,
        provider: String,
        credentials: Map<String, String>,
    ) {
        saved[account to provider] = credentials
        updatedAt[account to provider] = calendar.now()
    }

    override fun remove(
        account: String,
        provider: String,
    ): Boolean = (saved.remove(account to provider) != null).also { updatedAt.remove(account to provider) }

    override fun of(account: String): List<StoredCredentials> =
        saved.filterKeys { it.first == account }.map { (key, values) -> StoredCredentials(key.second, values.keys.sorted(), updatedAt.getValue(key)) }
}
