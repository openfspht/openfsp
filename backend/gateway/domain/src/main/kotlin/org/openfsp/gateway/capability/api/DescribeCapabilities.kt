package org.openfsp.gateway.capability.api

import org.openfsp.gateway.account.AccountFilter
import org.openfsp.gateway.capability.ProviderCapabilities

interface DescribeCapabilities {
    fun describe(accounts: AccountFilter): List<ProviderCapabilities>

    fun supported(): List<String>
}
