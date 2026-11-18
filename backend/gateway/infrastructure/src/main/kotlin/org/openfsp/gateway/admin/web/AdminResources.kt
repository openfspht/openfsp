package org.openfsp.gateway.admin.web

import java.time.Instant

data class AttestationRequest(
    val person: String,
    val status: String,
    val evidence: String,
    val failureReason: String? = null,
)

data class KeyRequest(
    val account: String,
    val principal: String,
    val scopes: List<String>,
    val expiresAt: Instant? = null,
)

data class IssuedKeyResource(
    val id: String,
    val secret: String,
)
