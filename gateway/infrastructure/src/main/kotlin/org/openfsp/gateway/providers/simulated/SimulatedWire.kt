package org.openfsp.gateway.providers.simulated

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.time.LocalDate

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SimulatedPaymentRequest(
    val orderId: String,
    val amount: String,
    val msisdn: String?,
    val description: String?,
    val callbackUrl: String,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SimulatedOperatorPayment(
    val paymentId: String,
    val orderId: String,
    val status: String,
    val amount: String,
    val reason: String?,
    val approval: String?,
    val expiresIn: Long?,
    val checkoutUrl: String?,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SimulatedCallback(
    val paymentId: String,
    val orderId: String,
    val status: String,
    val amount: String,
    val reason: String?,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SimulatedStatement(
    val businessDate: LocalDate,
    val closed: Boolean,
    val entries: List<SimulatedStatementEntry>,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SimulatedStatementEntry(
    val reference: String,
    val orderId: String?,
    val type: String,
    val amount: String,
    val bookedAt: String,
)

data class SimulatedError(
    val code: String?,
    val message: String?,
)
