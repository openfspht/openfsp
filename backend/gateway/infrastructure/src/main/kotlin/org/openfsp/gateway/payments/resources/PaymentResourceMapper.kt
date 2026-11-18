package org.openfsp.gateway.payments.resources

import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.provider.ProviderFee
import org.openfsp.protocol.api.Fee
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
            payer = payment.payerPhoneNumber?.let(::PayerResource),
            description = payment.description,
            nextAction = payment.nextAction,
            createdAt = payment.createdAt,
            updatedAt = payment.updatedAt,
            expiresAt = payment.expiresAt,
            completedAt = payment.completedAt,
            failureReason = payment.failureReason,
            failureDetail = payment.failureDetail,
            metadata = payment.publishedMetadata,
            fee = payment.fee?.let(::toResource),
        )

    fun toResource(fee: ProviderFee): Fee = Fee(fee.amount, fee.bearer.wire)
}
