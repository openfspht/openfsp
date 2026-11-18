package org.openfsp.gateway.admin.web

import org.openfsp.gateway.account.AccountFilter

object AdminAccounts {
    fun filter(account: String?): AccountFilter = account?.trim()?.takeIf { it.isNotEmpty() }?.let(AccountFilter::Only) ?: AccountFilter.Every
}
