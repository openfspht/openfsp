package org.openfsp.gateway.payments.web

data class MoneyRequest(
    val amount: Long?,
    val currency: String?,
)

data class PayerRequest(
    val phoneNumber: String?,
)

data class CreatePaymentRequest(
    val reference: String?,
    val amount: MoneyRequest?,
    val provider: String?,
    val payer: PayerRequest?,
    val description: String?,
    val returnUrl: String?,
    val expiresAt: String?,
    val locale: String?,
    val metadata: Map<String, Any?>?,
)
