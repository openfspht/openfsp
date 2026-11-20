package org.openfsp.gateway.account.api

import org.openfsp.gateway.account.MerchantAccount
import java.time.Instant

interface ManageAccounts {
    fun open(
        name: String,
        expiresAt: Instant? = null,
    ): MerchantAccount

    fun find(id: String): MerchantAccount?

    fun all(): List<MerchantAccount>
}
