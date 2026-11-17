package org.openfsp.gateway.payments.resources

import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.api.Fee
import java.time.Instant

data class PayerResource(
    val phoneNumber: String,
)

data class PaymentResource(
    val id: String,
    val reference: String,
    val status: PaymentStatus,
    val amount: Money,
    val provider: String,
    val payer: PayerResource?,
    val description: String?,
    val nextAction: NextAction?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant?,
    val completedAt: Instant?,
    val failureReason: FailureReason?,
    val failureDetail: Map<String, Any?>?,
    val metadata: Map<String, String>?,
    val fee: Fee? = null,
)
