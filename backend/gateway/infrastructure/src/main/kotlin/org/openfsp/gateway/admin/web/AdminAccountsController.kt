package org.openfsp.gateway.admin.web

import org.openfsp.gateway.account.api.ManageAccounts
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
    private val mapper: AccountResourceMapper,
) {
    @GetMapping
    fun list(): AccountCollection = mapper.toCollection(accounts.all())

    @PostMapping
    fun open(
        @RequestBody request: AccountRequest,
    ): ResponseEntity<AccountResource> = ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResource(accounts.open(mapper.toName(request))))
}
