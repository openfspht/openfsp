package org.openfsp.gateway.auth

import java.time.Instant

enum class ApiKeyState { ACTIVE, EXPIRED, REVOKED, ALL }

data class KeyFilters(
    val account: String?,
    val status: ApiKeyState,
    val query: String?,
)

data class KeyView(
    val id: String,
    val account: String,
    val principal: String,
    val prefix: String,
    val scopes: Set<Scope>,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val lastUsedAt: Instant?,
    val revokedAt: Instant?,
    val demoOnly: Boolean = false,
)
