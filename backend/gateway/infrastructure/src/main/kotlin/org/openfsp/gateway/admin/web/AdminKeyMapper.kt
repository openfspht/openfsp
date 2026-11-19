package org.openfsp.gateway.admin.web

import org.openfsp.gateway.auth.ApiKeyState
import org.openfsp.gateway.auth.IssuedKey
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.protocol.FieldErrorCode
import org.springframework.stereotype.Component
import java.time.Instant

data class KeyIssue(
    val account: String,
    val principal: String,
    val scopes: Set<Scope>,
    val expiresAt: Instant?,
)

@Component
class AdminKeyMapper {
    fun toState(status: String): ApiKeyState =
        ApiKeyState.entries.firstOrNull { it.name.equals(status, ignoreCase = true) } ?: throw OpenFspException.invalid("status", "must be active, expired, revoked or all")

    fun toIssue(request: KeyRequest): KeyIssue {
        if (request.account.isBlank()) throw OpenFspException.invalid("account", "is required", FieldErrorCode.REQUIRED)
        if (request.principal.isBlank()) throw OpenFspException.invalid("principal", "is required", FieldErrorCode.REQUIRED)
        val scopes = request.scopes.map { Scope.fromWire(it) ?: throw OpenFspException.invalid("scopes", "unknown scope $it") }.toSet()
        return KeyIssue(request.account.trim(), request.principal.trim(), scopes, request.expiresAt)
    }

    fun toResource(issued: IssuedKey): IssuedKeyResource = IssuedKeyResource(issued.keyId, issued.secret)
}
