package org.openfsp.gateway.admin.web

import org.openfsp.gateway.account.MerchantAccount
import org.openfsp.gateway.account.api.ManageAccounts
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.protocol.FieldErrorCode
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class AccountRequest(
    val name: String? = null,
)

data class AccountResource(
    val id: String,
    val name: String,
    val createdAt: Instant,
    val expiresAt: Instant?,
)

data class AccountCollection(
    val data: List<AccountResource>,
)

@RestController
@RequestMapping("/admin/accounts")
class AdminAccountsController(
    private val accounts: ManageAccounts,
) {
    @GetMapping
    fun list(): AccountCollection = AccountCollection(accounts.all().map { it.toResource() })

    @PostMapping
    fun open(
        @RequestBody request: AccountRequest,
    ): ResponseEntity<AccountResource> {
        val name = request.name?.trim()?.takeIf { it.isNotEmpty() } ?: throw OpenFspException.invalid("name", "is required", FieldErrorCode.REQUIRED)
        return ResponseEntity.status(HttpStatus.CREATED).body(accounts.open(name).toResource())
    }

    private fun MerchantAccount.toResource() = AccountResource(id, name, createdAt, expiresAt)
}
