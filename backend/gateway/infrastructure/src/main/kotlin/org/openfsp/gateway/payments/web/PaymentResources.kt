package org.openfsp.gateway.payments.web

import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.api.Fee
import java.time.Instant

data class MoneyRequest(
    val amount: Long?,
    val currency: String?,
)

data class PayerRequest(
    val phoneNumber: String?,
)

data class CreatePaymentRequest(
    val reference: String?,
    val amount: MoneyRequest?,
    val provider: String?,
    val payer: PayerRequest?,
    val description: String?,
    val returnUrl: String?,
    val expiresAt: String?,
    val locale: String?,
    val metadata: Map<String, Any?>?,
)

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

data class Collection<T>(
    val data: List<T>,
)
