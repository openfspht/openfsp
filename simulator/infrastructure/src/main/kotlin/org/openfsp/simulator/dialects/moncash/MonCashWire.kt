package org.openfsp.simulator.dialects.moncash

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class MonCashToken(
    @get:JsonProperty("access_token") val accessToken: String,
    @get:JsonProperty("token_type") val tokenType: String,
    @get:JsonProperty("expires_in") val expiresIn: Long,
    val scope: String,
)

data class MonCashCreatePayment(
    @param:JsonProperty("amount") val amount: BigDecimal,
    @param:JsonProperty("orderId") val orderId: String,
)

data class MonCashCreatedPayment(
    val path: String,
    @get:JsonProperty("payment_token") val paymentToken: MonCashPaymentToken,
    val status: Int,
)

data class MonCashPaymentToken(
    val expired: String,
    val created: String,
    val token: String,
)

data class MonCashOrderQuery(
    @param:JsonProperty("orderId") val orderId: String,
)

data class MonCashTransactionQuery(
    @param:JsonProperty("transactionId") val transactionId: String,
)

data class MonCashPaymentResult(
    val path: String,
    val payment: MonCashPayment,
    val status: Int,
)

data class MonCashPayment(
    val reference: String,
    @get:JsonProperty("transaction_id") val transactionId: String,
    val cost: BigDecimal,
    val message: String,
    val payer: String?,
)

data class MonCashError(
    val path: String,
    val error: String,
    val message: String,
    val status: Int,
)
