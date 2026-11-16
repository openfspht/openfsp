package org.openfsp.simulator.dialects.natcash

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class NatCashPaymentRequest(
    @param:JsonProperty("requestId") val requestId: String,
    @param:JsonProperty("partnerCode") val partnerCode: String,
    @param:JsonProperty("username") val username: String,
    @param:JsonProperty("password") val password: String,
    @param:JsonProperty("callbackUrl") val callbackUrl: String?,
    @param:JsonProperty("timestamp") val timestamp: Long,
    @param:JsonProperty("orderNumber") val orderNumber: String,
    @param:JsonProperty("amount") val amount: BigDecimal,
    @param:JsonProperty("signature") val signature: String,
    @param:JsonProperty("msisdn") val msisdn: String?,
    @param:JsonProperty("language") val language: String?,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class NatCashPaymentResponse(
    @get:JsonProperty("status") val status: Int,
    @get:JsonProperty("code") val code: String,
    @get:JsonProperty("message") val message: String,
    @get:JsonProperty("url") val url: String? = null,
    @get:JsonProperty("expiredAt") val expiredAt: Long? = null,
)

data class NatCashTransactionQuery(
    @param:JsonProperty("requestId") val requestId: String,
    @param:JsonProperty("username") val username: String,
    @param:JsonProperty("password") val password: String,
    @param:JsonProperty("partnerCode") val partnerCode: String,
    @param:JsonProperty("orderNumber") val orderNumber: String,
    @param:JsonProperty("signature") val signature: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class NatCashTransactionResponse(
    @get:JsonProperty("status") val status: Int,
    @get:JsonProperty("code") val code: String,
    @get:JsonProperty("message") val message: String,
    @get:JsonProperty("data") val data: NatCashTransaction? = null,
)

data class NatCashTransaction(
    @get:JsonProperty("amount") val amount: String,
    @get:JsonProperty("orderNumber") val orderNumber: String,
    @get:JsonProperty("responseCode") val responseCode: String,
    @get:JsonProperty("transId") val transId: String,
)

data class NatCashCallback(
    @get:JsonProperty("orderNumber") val orderNumber: String,
    @get:JsonProperty("code") val code: String,
    @get:JsonProperty("transId") val transId: String,
    @get:JsonProperty("signature") val signature: String,
)
