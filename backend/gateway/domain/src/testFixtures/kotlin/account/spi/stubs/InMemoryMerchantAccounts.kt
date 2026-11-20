package org.openfsp.gateway.account.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.account.MerchantAccount
import org.openfsp.gateway.account.spi.MerchantAccounts
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Stub
class InMemoryMerchantAccounts : MerchantAccounts {
    private val accounts = ConcurrentHashMap<String, MerchantAccount>()

    init {
        accounts[MerchantAccount.DEFAULT] = MerchantAccount(MerchantAccount.DEFAULT, "default", Instant.EPOCH)
    }

    override fun insert(account: MerchantAccount): Boolean =
        synchronized(accounts) {
            if (accounts.values.any { it.name == account.name }) return false
            accounts[account.id] = account
            true
        }

    override fun find(id: String): MerchantAccount? = accounts[id]

    override fun all(): List<MerchantAccount> = accounts.values.sortedWith(compareBy({ it.createdAt }, { it.id }))
}
