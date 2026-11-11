package org.openfsp.gateway.providers.moncash

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class MonCashTokenResponse(
    @param:JsonProperty("access_token") val accessToken: String?,
    @param:JsonProperty("expires_in") val expiresIn: Long?,
)

data class MonCashCreatePayment(
    val amount: BigDecimal,
    val orderId: String,
)

data class MonCashCreatedPayment(
    @param:JsonProperty("payment_token") val paymentToken: MonCashPaymentToken?,
)

data class MonCashPaymentToken(
    val token: String?,
    val expired: String?,
)

data class MonCashOrderQuery(
    val orderId: String,
)

data class MonCashTransactionQuery(
    val transactionId: String,
)

data class MonCashPaymentResult(
    val payment: MonCashPayment?,
)

data class MonCashPayment(
    val reference: String?,
    @param:JsonProperty("transaction_id") val transactionId: String?,
    val cost: String?,
    val message: String?,
)

data class MonCashError(
    val error: String?,
    val message: String?,
)
