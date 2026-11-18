package org.openfsp.gateway.providers.openfsp

import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.provider.ProviderPayment
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.providers.SecretRedaction
import org.openfsp.protocol.Effect
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import org.openfsp.sdk.ApiException
import org.openfsp.sdk.OpenFspException
import org.openfsp.sdk.TransportException
import org.openfsp.sdk.UnknownPaymentStatusException
import org.openfsp.sdk.model.Payment
import org.openfsp.sdk.model.PaymentRequest
import org.openfsp.sdk.model.Provider
import java.net.ConnectException
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpConnectTimeoutException
import org.openfsp.sdk.model.Money as SdkMoney
import org.openfsp.sdk.model.NextAction as SdkNextAction

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
        amount = SdkMoney.centimes(request.amount.amount),
        provider = providerId,
        payerPhoneNumber = request.payerPhoneNumber,
        description = request.description,
        returnUrl = request.returnUrl?.toString(),
        callbackUrl = request.callbackUrl.toString().takeIf { descriptor.paymentUrl },
        expiresAt = request.expiresAt,
        locale = request.locale,
    )

    fun toPayment(payment: Payment): ProviderPayment {
        val report = toReport(payment)
        val nextAction =
            when (val action = payment.nextAction) {
                is SdkNextAction.Redirect -> NextAction.redirect(action.url, action.expiresAt)
                SdkNextAction.PayerApproval -> NextAction.payerApproval
                SdkNextAction.None ->
                    if (report.status.isTerminal) NextAction(NextAction.Type.NONE) else throw ProviderFailure.Rejected("A pending payment without next action from the provider")
            }
        return ProviderPayment(report, nextAction, payment.expiresAt)
    }

    fun toReport(payment: Payment): ProviderReport {
        val failed = payment.status == PaymentStatus.FAILED
        return ProviderReport(
            orderId = payment.reference,
            providerReference = payment.id,
            status = payment.status,
            amount = toMoney(payment.amount),
            failureReason = (payment.failureReason ?: FailureReason.UNSPECIFIED).takeIf { failed },
            failureDetail = (payment.failureDetail.ifEmpty { payment.failureReason?.let { mapOf(CODE to it.wire) } })?.takeIf { failed }?.let(redaction::redact),
        )
    }

    fun undecided(unknown: UnknownPaymentStatusException) = ProviderReport(unknown.reference, unknown.paymentId, PaymentStatus.PENDING, amount = null)

    fun toDescriptor(
        announced: Provider,
        displayName: String?,
        logoUrl: URI?,
    ) = ProviderDescriptor(
        displayName = displayName ?: announced.displayName,
        capabilities = announced.capabilities.filter { it in ProviderDescriptor.PROVIDER_CAPABILITIES }.toSet() + ProviderDescriptor.PAYMENTS,
        nextActions = announced.nextActions.mapNotNull { wire -> NextAction.Type.entries.firstOrNull { it.wire == wire } }.toSet(),
        payerRequired = announced.payerRequired,
        returnUrlRequired = announced.returnUrlRequired,
        expiryGuaranteed = announced.expiryGuaranteed,
        locales = announced.locales.filter { it in LOCALES }.toSet(),
        idempotentCreation = true,
        logoUrl = logoUrl ?: announced.logoUrl?.let { runCatching { URI(it) }.getOrNull()?.takeIf(URI::isAbsolute) },
        signedCallbacks = announced.supports(PER_PAYMENT_URL) || announced.supports(ProviderDescriptor.WEBHOOKS_EMIT),
        paymentUrl = announced.supports(PER_PAYMENT_URL),
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

    fun toFailure(failure: OpenFspException): ProviderFailure =
        when (failure) {
            is TransportException ->
                when (failure.cause) {
                    is ConnectException, is UnknownHostException, is HttpConnectTimeoutException -> ProviderFailure.Unavailable(failure)
                    else -> ProviderFailure.Timeout(failure)
                }
            is ApiException ->
                if (failure.code == IN_PROGRESS) {
                    ProviderFailure.InProgress(failure.retryAfter)
                } else {
                    val detail = mapOf(CODE to failure.code, MESSAGE to (failure.detail ?: failure.title))
                    ProviderFailure.Rejected("The provider answered ${failure.status}", redaction.redact(detail), failure.status in CLIENT_ERROR && failure.effect == Effect.NONE)
                }
            else -> ProviderFailure.Rejected(failure.message ?: "The request was refused before it was sent", withoutEffect = true)
        }

    private fun toMoney(money: SdkMoney): Money = Money.htg(money.centimes)

    private companion object {
        const val CREATION_KEY_PREFIX = "create-"
        const val CODE = "code"
        const val MESSAGE = "message"
        const val IN_PROGRESS = "idempotency-request-in-progress"
        const val PER_PAYMENT_URL = "webhooks.per_payment_url"
        val CLIENT_ERROR = 400..499
        val LOCALES = setOf("fr", "ht", "en")
    }
}
