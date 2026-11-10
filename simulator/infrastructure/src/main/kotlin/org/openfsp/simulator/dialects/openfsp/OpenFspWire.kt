package org.openfsp.simulator.dialects.openfsp

data class OpenFspPaymentRequest(
    val orderId: String,
    val amount: String,
    val msisdn: String?,
    val description: String?,
    val callbackUrl: String?,
)

data class OpenFspPayment(
    val paymentId: String,
    val orderId: String,
    val status: String,
    val amount: String,
    val reason: String?,
    val approval: String,
    val expiresIn: Long?,
    val checkoutUrl: String?,
)

data class OpenFspCallback(
    val paymentId: String,
    val orderId: String,
    val status: String,
    val amount: String,
    val reason: String?,
)

data class OpenFspError(
    val code: String,
    val message: String,
)
