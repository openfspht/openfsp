package org.openfsp.gateway.providers.moncash

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.provider.ProviderPayment
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.providers.DecimalAmounts
import org.openfsp.gateway.providers.SecretRedaction
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MonCashMapper(
    private val amounts: DecimalAmounts,
    private val calendar: BusinessCalendar,
    private val checkoutBase: URI,
    private val redaction: SecretRedaction,
) {
    fun toCreatePayment(request: ProviderPaymentRequest) = MonCashCreatePayment(amounts.toNumber(request.amount), request.orderId)

    fun toPayment(
        orderId: String,
        created: MonCashCreatedPayment,
    ): ProviderPayment {
        val token = created.paymentToken?.token ?: throw ProviderFailure.Rejected("MonCash returned no payment token")
        val redirect = UriComponentsBuilder.fromUri(checkoutBase).path(REDIRECT_PATH).queryParam("token", token).build().toUriString()
        val expiresAt = created.paymentToken.expired?.let(::expiry)
        return ProviderPayment(pending(orderId), NextAction.redirect(redirect, expiresAt), expiresAt)
    }

    fun toReport(
        orderId: String,
        payment: MonCashPayment?,
    ): ProviderReport {
        payment ?: return pending(orderId)
        val status = statusOf(payment.message)
        val failed = status == PaymentStatus.FAILED
        return ProviderReport(
            orderId = payment.reference ?: orderId,
            providerReference = payment.transactionId,
            status = status,
            amount = payment.cost?.let(amounts::fromDecimal),
            failureReason = FailureReason.UNSPECIFIED.takeIf { failed },
            failureDetail = redaction.redact(mapOf("code" to payment.message)).takeIf { failed },
        )
    }

    private fun statusOf(message: String?): PaymentStatus =
        when (message) {
            SUCCESSFUL -> PaymentStatus.SUCCEEDED
            FAILED -> PaymentStatus.FAILED
            else -> PaymentStatus.PENDING
        }

    private fun pending(orderId: String) = ProviderReport(orderId, null, PaymentStatus.PENDING, null)

    private fun expiry(value: String): Instant? = runCatching { calendar.fromLocal(LocalDateTime.parse(value, EXPIRY)) }.getOrNull()

    private companion object {
        const val SUCCESSFUL = "successful"
        const val FAILED = "failed"
        const val REDIRECT_PATH = "/Payment/Redirect"
        val EXPIRY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS")
    }
}
