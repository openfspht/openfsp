package org.openfsp.gateway.controllers.payments

import org.openfsp.gateway.payment.Payment
import org.springframework.stereotype.Component

@Component
class PaymentResourceMapper {
    fun toResource(payment: Payment): PaymentResource =
        PaymentResource(
            id = payment.id,
            reference = payment.reference,
            status = payment.status,
            amount = payment.amount,
            provider = payment.provider,
            providerReference = payment.providerReference,
            providerOrderId = payment.providerOrderId,
            payer = payment.payerPhoneNumber?.let(::PayerResource),
            description = payment.description,
            nextAction = payment.nextAction,
            createdAt = payment.createdAt,
            updatedAt = payment.updatedAt,
            expiresAt = payment.expiresAt,
            completedAt = payment.completedAt,
            failureReason = payment.failureReason,
            failureDetail = payment.failureDetail,
            metadata = payment.metadata,
        )
}
