package org.openfsp.gateway.account.spi

import org.openfsp.gateway.account.MerchantAccount

interface MerchantAccounts {
    fun insert(account: MerchantAccount): Boolean

    fun find(id: String): MerchantAccount?

    fun all(): List<MerchantAccount>
}
