package org.openfsp.gateway.admin.web

import org.openfsp.gateway.account.MerchantAccount
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.protocol.FieldErrorCode
import org.springframework.stereotype.Component

@Component
class AccountResourceMapper {
    fun toName(request: AccountRequest): String = request.name?.trim()?.takeIf { it.isNotEmpty() } ?: throw OpenFspException.invalid("name", "is required", FieldErrorCode.REQUIRED)

    fun toResource(account: MerchantAccount): AccountResource = AccountResource(account.id, account.name, account.createdAt, account.expiresAt)

    fun toCollection(accounts: List<MerchantAccount>): AccountCollection = AccountCollection(accounts.map(::toResource))
}
