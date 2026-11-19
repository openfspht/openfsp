package org.openfsp.gateway.admin.web

import org.openfsp.gateway.account.AccountFilter
import org.springframework.stereotype.Component

@Component
class AccountFilterMapper {
    fun toFilter(account: String?): AccountFilter = account?.trim()?.takeIf { it.isNotEmpty() }?.let(AccountFilter::Only) ?: AccountFilter.Every
}
