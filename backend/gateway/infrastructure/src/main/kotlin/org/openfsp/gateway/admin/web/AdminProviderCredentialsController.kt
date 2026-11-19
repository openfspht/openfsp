package org.openfsp.gateway.admin.web

import org.openfsp.gateway.provider.api.ManageProviderCredentials
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Instant

data class CredentialsRequest(
    val credentials: Map<String, String?>? = null,
)

data class AccountProviderResource(
    val provider: String,
    val displayName: String,
    val source: String,
    val acceptsAccountCredentials: Boolean,
    val credentialNames: List<String>,
    val updatedAt: Instant?,
    val notificationUrl: URI?,
    val returnUrl: URI?,
    val revisions: List<CredentialRevisionResource>,
)

data class CredentialRevisionResource(
    val id: String,
    val status: String,
    val sealed: Boolean,
    val createdAt: Instant,
    val retiredAt: Instant?,
    val revokedAt: Instant?,
)

data class AccountProviderCollection(
    val data: List<AccountProviderResource>,
)

@RestController
@RequestMapping("/admin/accounts/{account}/providers")
class AdminProviderCredentialsController(
    private val credentials: ManageProviderCredentials,
    private val mapper: AccountProviderMapper,
) {
    @GetMapping
    fun list(
        @PathVariable account: String,
    ): AccountProviderCollection = mapper.toCollection(account, credentials.providersOf(account))

    @PutMapping("/{provider}/credentials")
    fun save(
        @PathVariable account: String,
        @PathVariable provider: String,
        @RequestBody request: CredentialsRequest,
    ): ResponseEntity<Void> {
        credentials.save(account, provider, request.credentials)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{provider}/credentials")
    fun remove(
        @PathVariable account: String,
        @PathVariable provider: String,
    ): ResponseEntity<Void> {
        credentials.remove(account, provider)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{provider}/revisions/{revision}/revoke")
    fun revoke(
        @PathVariable account: String,
        @PathVariable provider: String,
        @PathVariable revision: String,
    ): ResponseEntity<Void> {
        credentials.revoke(account, provider, revision)
        return ResponseEntity.noContent().build()
    }
}
