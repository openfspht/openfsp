package org.openfsp.gateway.providers.natcash

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.CallbackRequest
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.provider.ProviderPayment
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.providers.DecimalAmounts
import org.openfsp.gateway.providers.ProviderJson
import org.openfsp.gateway.providers.SecretRedaction
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import java.util.UUID

class NatCashMapper(
    private val settings: NatCashSettings,
    private val signatures: NatCashSignatures,
    private val amounts: DecimalAmounts,
    private val calendar: BusinessCalendar,
    private val json: ProviderJson,
    private val redaction: SecretRedaction,
) {
    fun toPaymentRequest(request: ProviderPaymentRequest): NatCashPaymentRequest {
        val unsigned =
            NatCashPaymentRequest(
                requestId = UUID.randomUUID().toString(),
                partnerCode = settings.partnerCode,
                username = settings.username,
                password = settings.password,
                callbackUrl = request.callbackUrl.toString(),
                timestamp = calendar.now().toEpochMilli(),
                orderNumber = request.orderId,
                amount = amounts.toNumber(request.amount),
                signature = "",
                msisdn = request.payerPhoneNumber?.removePrefix("+"),
                language = request.locale,
            )
        return unsigned.copy(signature = signatures.of(unsigned))
    }

    fun toTransactionQuery(orderId: String): NatCashTransactionQuery {
        val unsigned = NatCashTransactionQuery(UUID.randomUUID().toString(), settings.username, settings.password, settings.partnerCode, orderId, "")
        return unsigned.copy(signature = signatures.of(unsigned))
    }

    fun toPayment(
        orderId: String,
        response: NatCashPaymentResponse,
    ): ProviderPayment {
        val url = response.url ?: throw ProviderFailure.Rejected("NatCash returned no payment URL")
        return ProviderPayment(pending(orderId), NextAction.redirect(url, null), null)
    }

    fun toReport(
        orderId: String,
        transaction: NatCashTransaction?,
    ): ProviderReport {
        transaction ?: return pending(orderId)
        return report(
            orderId = transaction.orderNumber ?: orderId,
            reference = transaction.transId,
            code = transaction.responseCode.orEmpty(),
        ).copy(amount = transaction.amount?.let(amounts::fromGrouped))
    }

    fun toCallback(request: CallbackRequest): NatCashCallback? {
        val parameters = request.parameters
        if (parameters.containsKey(ORDER_NUMBER)) {
            return NatCashCallback(parameters[ORDER_NUMBER], parameters[CODE], parameters[TRANS_ID], parameters[SIGNATURE])
        }
        return json.readOrNull(request.body, NatCashCallback::class.java)
    }

    fun toReport(callback: NatCashCallback): ProviderReport? {
        val orderNumber = callback.orderNumber ?: return null
        return report(orderNumber, callback.transId, callback.code ?: return null)
    }

    private fun report(
        orderId: String,
        reference: String?,
        code: String,
    ): ProviderReport {
        val status = statusOf(code)
        val failed = status == PaymentStatus.FAILED
        return ProviderReport(
            orderId = orderId,
            providerReference = reference,
            status = status,
            amount = null,
            failureReason = FailureReason.UNSPECIFIED.takeIf { failed },
            failureDetail = redaction.redact(mapOf("code" to code)).takeIf { failed },
        )
    }

    private fun statusOf(code: String): PaymentStatus =
        when (code) {
            PAID -> PaymentStatus.SUCCEEDED
            DECLINED -> PaymentStatus.FAILED
            else -> PaymentStatus.PENDING
        }

    private fun pending(orderId: String) = ProviderReport(orderId, null, PaymentStatus.PENDING, null)

    private companion object {
        const val PAID = "1"
        const val DECLINED = "-1"
        const val ORDER_NUMBER = "orderNumber"
        const val CODE = "code"
        const val TRANS_ID = "transId"
        const val SIGNATURE = "signature"
    }
}
