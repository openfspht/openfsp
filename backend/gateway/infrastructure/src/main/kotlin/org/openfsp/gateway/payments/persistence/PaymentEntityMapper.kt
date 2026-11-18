package org.openfsp.gateway.payments.persistence

import org.openfsp.gateway.payment.CreationRequest
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.provider.CredentialSource
import org.openfsp.gateway.provider.FeeBearer
import org.openfsp.gateway.provider.ProviderFee
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
            accountId = entity.accountId,
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
            locale = entity.locale,
            expiresAt = entity.expiresAt,
            completedAt = entity.completedAt,
            failureReason = entity.failureReason?.let(FailureReason::fromWire),
            failureDetail = entity.failureDetail?.let { json.readValue<Map<String, Any?>>(it) },
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            callbackTokenDigest = entity.callbackTokenDigest?.let(HexFormat.of()::formatHex),
            creationOutcomeUnknown = entity.creationOutcomeUnknown,
            creationRequest = creationRequestOf(entity.creationPrincipal, entity.idempotencyKey, entity.requestFingerprint),
            fee = feeOf(entity.fee, entity.feeBearer),
            version = entity.version,
            credentials = CredentialSource.fromWire(entity.providerCredentials),
        )

    fun toEntity(payment: Payment): PaymentJpaEntity =
        PaymentJpaEntity(
            id = payment.id,
            accountId = payment.accountId,
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
            locale = payment.locale,
            expiresAt = payment.expiresAt,
            completedAt = payment.completedAt,
            failureReason = payment.failureReason?.wire,
            failureDetail = payment.failureDetail?.let(json::writeValueAsString),
            createdAt = payment.createdAt,
            updatedAt = payment.updatedAt,
            callbackTokenDigest = payment.callbackTokenDigest?.let(HexFormat.of()::parseHex),
            creationOutcomeUnknown = payment.creationOutcomeUnknown,
            creationPrincipal = payment.creationRequest?.principal,
            idempotencyKey = payment.creationRequest?.idempotencyKey,
            requestFingerprint = payment.creationRequest?.fingerprint,
            fee = payment.fee?.amount?.amount,
            feeBearer = payment.fee?.bearer?.wire,
            version = payment.version,
            providerCredentials = payment.credentials.wire,
        )

    private fun creationRequestOf(
        principal: String?,
        key: String?,
        fingerprint: String?,
    ): CreationRequest? = if (principal != null && key != null && fingerprint != null) CreationRequest(principal, key, fingerprint) else null

    private fun feeOf(
        amount: Long?,
        bearer: String?,
    ): ProviderFee? = if (amount != null && bearer != null) ProviderFee(Money.htg(amount), FeeBearer.fromWire(bearer) ?: error("stored fee bearer $bearer is unknown")) else null

    fun digest(hex: String): ByteArray = HexFormat.of().parseHex(hex)
}
