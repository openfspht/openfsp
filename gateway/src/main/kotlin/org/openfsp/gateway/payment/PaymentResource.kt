package org.openfsp.gateway.payment

import org.openfsp.protocol.Currency
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.Instant

data class Payer(
    val phoneNumber: String,
)

data class PaymentResource(
    val id: String,
    val reference: String,
    val status: PaymentStatus,
    val amount: Money,
    val provider: String,
    val providerReference: String?,
    val providerOrderId: String,
    val payer: Payer?,
    val description: String?,
    val nextAction: NextAction?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant?,
    val completedAt: Instant?,
    val failureReason: FailureReason?,
    val failureDetail: Map<String, Any?>?,
    val metadata: Map<String, String>?,
) {
    companion object {
        fun of(
            payment: PaymentEntity,
            json: JsonMapper,
        ) = PaymentResource(
            id = payment.id,
            reference = payment.reference,
            status = PaymentStatus.fromWire(payment.status) ?: error("stored status ${payment.status} is unknown"),
            amount = Money(payment.amount, Currency.valueOf(payment.currency)),
            provider = payment.provider,
            providerReference = payment.providerReference,
            providerOrderId = payment.providerOrderId,
            payer = payment.payerPhoneNumber?.let(::Payer),
            description = payment.description,
            nextAction = payment.nextAction?.let { json.readValue<NextAction>(it) },
            createdAt = payment.createdAt,
            updatedAt = payment.updatedAt,
            expiresAt = payment.expiresAt,
            completedAt = payment.completedAt,
            failureReason = payment.failureReason?.let(FailureReason::fromWire),
            failureDetail = payment.failureDetail?.let { json.readValue<Map<String, Any?>>(it) },
            metadata = payment.metadataJson?.let { json.readValue<Map<String, String>>(it) },
        )
    }
}

data class PaymentCollection(
    val data: List<PaymentResource>,
)
