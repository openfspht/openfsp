package org.openfsp.gateway.providers.openfsp

import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.provider.ProviderPayment
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.ProviderStatement
import org.openfsp.gateway.provider.ProviderStatementLine
import org.openfsp.gateway.providers.SecretRedaction
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.api.PayerResource
import org.openfsp.protocol.api.PaymentRequest
import org.openfsp.protocol.api.PaymentResource
import org.openfsp.protocol.api.ProviderCapabilitiesResource
import org.openfsp.protocol.api.StatementResource
import java.net.URI
import java.time.LocalDate

class OpenFspMapper(
    private val providerId: String,
    private val redaction: SecretRedaction,
) {
    fun idempotencyKeyOf(orderId: String): String = "$CREATION_KEY_PREFIX$orderId"

    fun toPaymentRequest(
        request: ProviderPaymentRequest,
        descriptor: ProviderDescriptor,
    ) = PaymentRequest(
        reference = request.orderId,
        amount = request.amount,
        provider = providerId,
        payer = request.payerPhoneNumber?.let(::PayerResource),
        description = request.description,
        returnUrl = request.returnUrl?.toString(),
        locale = request.locale,
        callbackUrl = request.callbackUrl.toString().takeIf { descriptor.hasSignedCallbacks || descriptor.hasPaymentUrl },
    )

    fun toPayment(resource: PaymentResource): ProviderPayment {
        val report = toReport(resource)
        val nextAction =
            resource.nextAction
                ?: if (report.status.isTerminal) NextAction(NextAction.Type.NONE) else throw ProviderFailure.Rejected("A pending payment without next action from the provider")
        return ProviderPayment(report, nextAction, resource.expiresAt)
    }

    fun toReport(resource: PaymentResource): ProviderReport {
        val status = PaymentStatus.fromWire(resource.status) ?: PaymentStatus.PENDING
        val failed = status == PaymentStatus.FAILED
        return ProviderReport(
            orderId = resource.reference,
            providerReference = resource.id,
            status = status,
            amount = resource.amount,
            failureReason = (resource.failureReason?.let(FailureReason::fromWire) ?: FailureReason.UNSPECIFIED).takeIf { failed },
            failureDetail = (resource.failureDetail ?: resource.failureReason?.let { mapOf(CODE to it) })?.takeIf { failed }?.let(redaction::redact),
        )
    }

    fun toStatement(
        day: LocalDate,
        statement: StatementResource?,
    ): ProviderStatement {
        if (statement == null) return ProviderStatement(day, closed = false, lines = emptyList())
        return ProviderStatement(
            businessDate = statement.businessDate,
            closed = statement.complete,
            lines =
                statement.entries.map { entry ->
                    ProviderStatementLine(
                        providerReference = entry.providerReference,
                        orderId = entry.providerOrderId,
                        type = entry.type.takeIf { it in LINE_TYPES } ?: ADJUSTMENT,
                        amount = entry.amount,
                        feeAmount = entry.feeAmount,
                        netAmount = entry.netAmount,
                        bookedAt = entry.bookedAt,
                    )
                },
        )
    }

    fun toDescriptor(
        announced: ProviderCapabilitiesResource,
        displayName: String?,
        logoUrl: URI?,
    ) = ProviderDescriptor(
        displayName = displayName ?: announced.displayName,
        capabilities = announced.capabilities.filter { it in SERVED }.toSet() + ProviderDescriptor.PAYMENTS,
        nextActions = announced.payments.nextActions.mapNotNull { wire -> NextAction.Type.entries.firstOrNull { it.wire == wire } }.toSet(),
        payerRequired = announced.payments.payerRequired,
        returnUrlRequired = announced.payments.returnUrlRequired,
        expiryGuaranteed = announced.payments.expiryGuaranteed,
        locales = announced.payments.locales.filter { it in LOCALES }.toSet(),
        idempotentCreation = true,
        logoUrl = logoUrl ?: announced.logoUrl?.let { runCatching { URI(it) }.getOrNull()?.takeIf(URI::isAbsolute) },
    )

    fun unreachable(
        displayName: String?,
        logoUrl: URI?,
    ) = ProviderDescriptor(
        displayName = displayName ?: providerId,
        capabilities = setOf(ProviderDescriptor.PAYMENTS),
        nextActions = emptySet(),
        payerRequired = false,
        idempotentCreation = true,
        logoUrl = logoUrl,
    )

    private companion object {
        const val CREATION_KEY_PREFIX = "create-"
        const val CODE = "code"
        const val ADJUSTMENT = "adjustment"
        val LINE_TYPES = setOf("payment", "reversal", "fee", ADJUSTMENT)
        val LOCALES = setOf("fr", "ht", "en")
        val SERVED = ProviderDescriptor.SUPPORTED - ProviderDescriptor.WEBHOOKS_EMIT
    }
}
