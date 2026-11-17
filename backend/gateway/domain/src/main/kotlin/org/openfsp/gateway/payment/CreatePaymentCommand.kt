package org.openfsp.gateway.payment

import org.openfsp.protocol.Money
import java.net.URI
import java.time.Instant

data class CreatePaymentCommand(
    val reference: String,
    val amount: Money,
    val provider: String,
    val payerPhoneNumber: String? = null,
    val description: String? = null,
    val returnUrl: URI? = null,
    val expiresAt: Instant? = null,
    val locale: String? = null,
    val metadata: Map<String, String>? = null,
    val request: CreationRequest? = null,
)

data class CreationRequest(
    val principal: String,
    val idempotencyKey: String,
    val fingerprint: String,
)
