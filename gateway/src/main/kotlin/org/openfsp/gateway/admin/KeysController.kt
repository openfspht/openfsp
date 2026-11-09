package org.openfsp.gateway.admin

import org.openfsp.gateway.auth.ApiKeys
import org.openfsp.gateway.auth.KeySummary
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.web.ApiException
import org.openfsp.protocol.FieldError
import org.openfsp.protocol.ProblemType
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class KeyRequest(
    val principal: String,
    val scopes: List<String>,
    val expiresAt: Instant? = null,
)

data class IssuedKeyResponse(
    val id: String,
    val secret: String,
)

data class KeyCollection(
    val data: List<KeySummary>,
)

@RestController
@RequestMapping("/admin/keys")
class KeysController(
    private val keys: ApiKeys,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun list(): KeyCollection = KeyCollection(keys.list())

    @PostMapping
    fun issue(
        @RequestBody request: KeyRequest,
    ): ResponseEntity<IssuedKeyResponse> {
        val scopes = request.scopes.map { Scope.fromWire(it) ?: throw ApiException(ProblemType.INVALID_FIELD, errors = listOf(FieldError("scopes", "unknown scope $it"))) }
        if (request.principal.isBlank()) throw ApiException(ProblemType.INVALID_FIELD, errors = listOf(FieldError("principal", "is required")))
        val issued = keys.issue(request.principal.trim(), scopes.toSet(), request.expiresAt)
        log.info("Issued API key {} for {} with {}", issued.keyId, request.principal, scopes.map { it.wire })
        return ResponseEntity.status(HttpStatus.CREATED).body(IssuedKeyResponse(issued.keyId, issued.secret))
    }

    @PostMapping("/{id}/revoke")
    fun revoke(
        @PathVariable id: String,
    ): ResponseEntity<Void> {
        if (!keys.revoke(id)) return ResponseEntity.notFound().build()
        log.info("Revoked API key {}", id)
        return ResponseEntity.noContent().build()
    }
}
