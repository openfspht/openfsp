package org.openfsp.gateway.providers.natcash

import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigDecimal

@JsonInclude(JsonInclude.Include.NON_NULL)
data class NatCashPaymentRequest(
    val requestId: String,
    val partnerCode: String,
    val username: String,
    val password: String,
    val callbackUrl: String,
    val timestamp: Long,
    val orderNumber: String,
    val amount: BigDecimal,
    val signature: String,
    val msisdn: String?,
    val language: String?,
)

data class NatCashPaymentResponse(
    val status: Int?,
    val code: String?,
    val message: String?,
    val url: String?,
)

data class NatCashError(
    val code: String?,
    val message: String?,
)

data class NatCashTransactionQuery(
    val requestId: String,
    val username: String,
    val password: String,
    val partnerCode: String,
    val orderNumber: String,
    val signature: String,
)

data class NatCashTransactionResponse(
    val status: Int?,
    val code: String?,
    val message: String?,
    val data: NatCashTransaction?,
)

data class NatCashTransaction(
    val amount: String?,
    val orderNumber: String?,
    val responseCode: String?,
    val transId: String?,
)

data class NatCashCallback(
    val orderNumber: String?,
    val code: String?,
    val transId: String?,
    val signature: String?,
)
