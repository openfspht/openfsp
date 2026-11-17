package org.openfsp.gateway.admin.web

import org.openfsp.gateway.payments.resources.PaymentResource
import org.openfsp.protocol.PaymentStatus
import java.time.Instant

data class AttestationRequest(
    val person: String,
    val status: String,
    val evidence: String,
    val failureReason: String? = null,
)

data class KeyRequest(
    val principal: String,
    val scopes: List<String>,
    val expiresAt: Instant? = null,
)

data class IssuedKeyResource(
    val id: String,
    val secret: String,
)
