package org.openfsp.gateway.payments.persistence

import org.openfsp.gateway.payment.Payment
import org.openfsp.protocol.Currency
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.util.HexFormat

@Component
class PaymentEntityMapper(
    private val json: JsonMapper,
) {
    fun toDomain(entity: PaymentJpaEntity): Payment =
        Payment(
            id = entity.id,
            reference = entity.reference,
            amount = Money(entity.amount, Currency.valueOf(entity.currency)),
            provider = entity.provider,
            providerOrderId = entity.providerOrderId,
            status = PaymentStatus.fromWire(entity.status) ?: error("stored status ${entity.status} is unknown"),
            providerReference = entity.providerReference,
            payerPhoneNumber = entity.payerPhoneNumber,
            description = entity.description,
            returnUrl = entity.returnUrl?.let(::URI),
            nextAction = entity.nextAction?.let { json.readValue<NextAction>(it) },
            metadata = entity.metadataJson?.let { json.readValue<Map<String, String>>(it) },
            expiresAt = entity.expiresAt,
            completedAt = entity.completedAt,
            failureReason = entity.failureReason?.let(FailureReason::fromWire),
            failureDetail = entity.failureDetail?.let { json.readValue<Map<String, Any?>>(it) },
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            callbackTokenDigest = entity.callbackTokenDigest?.let(HexFormat.of()::formatHex),
        )

    fun toEntity(payment: Payment): PaymentJpaEntity =
        PaymentJpaEntity(
            id = payment.id,
            reference = payment.reference,
            status = payment.status.wire,
            amount = payment.amount.amount,
            currency = payment.amount.currency.code,
            provider = payment.provider,
            providerReference = payment.providerReference,
            providerOrderId = payment.providerOrderId,
            payerPhoneNumber = payment.payerPhoneNumber,
            description = payment.description,
            returnUrl = payment.returnUrl?.toString(),
            nextAction = payment.nextAction?.let(json::writeValueAsString),
            metadataJson = payment.metadata?.let(json::writeValueAsString),
            expiresAt = payment.expiresAt,
            completedAt = payment.completedAt,
            failureReason = payment.failureReason?.wire,
            failureDetail = payment.failureDetail?.let(json::writeValueAsString),
            createdAt = payment.createdAt,
            updatedAt = payment.updatedAt,
            callbackTokenDigest = payment.callbackTokenDigest?.let(HexFormat.of()::parseHex),
        )

    fun digest(hex: String): ByteArray = HexFormat.of().parseHex(hex)
}
