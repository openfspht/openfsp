package org.openfsp.simulator.control.web

import org.openfsp.simulator.payment.Outcome
import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.scenario.Scenario
import java.time.Instant

data class SimulatedPaymentResource(
    val operator: String,
    val paymentId: String,
    val orderId: String,
    val amount: String,
    val msisdn: String?,
    val callbackUrl: String?,
    val scenario: Scenario,
    val createdAt: Instant,
    val expiresAt: Instant,
    val outcome: Outcome?,
    val reason: String?,
    val reportedAmount: String?,
    val callbacksSent: Int,
    val lookups: Int,
    val description: String?,
    val metadata: Map<String, String>?,
    val locale: String?,
    val returnUrl: String?,
    val settledAt: Instant?,
)

fun SimulatedPayment.toResource() =
    SimulatedPaymentResource(
        operator = operator,
        paymentId = paymentId,
        orderId = reference,
        amount = amount,
        msisdn = msisdn,
        callbackUrl = callbackUrl,
        scenario = scenario,
        createdAt = createdAt,
        expiresAt = expiresAt,
        outcome = outcome,
        reason = reason,
        reportedAmount = reportedAmount,
        callbacksSent = callbacksSent,
        lookups = lookups,
        description = description,
        metadata = metadata,
        locale = locale,
        returnUrl = returnUrl,
        settledAt = settledAt,
    )
