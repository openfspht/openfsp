package org.openfsp.simulator.operator

import org.openfsp.simulator.Outcome
import org.openfsp.simulator.Scenario
import org.openfsp.simulator.SimulatedPayment
import org.openfsp.simulator.SimulatorProperties
import org.openfsp.simulator.SimulatorState
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest
import java.time.Duration

data class AlphaCreatePayment(
    val orderId: String,
    val amount: String,
    val msisdn: String?,
    val description: String?,
    val callbackUrl: String?,
)

data class AlphaPayment(
    val paymentId: String,
    val orderId: String,
    val status: String,
    val amount: String,
    val reason: String?,
    val approval: String,
    val expiresIn: Long?,
)

data class AlphaError(
    val code: String,
    val message: String,
)

@RestController
@RequestMapping("/${AlphaOperator.ID}/v1")
class AlphaOperator(
    private val state: SimulatorState,
    private val properties: SimulatorProperties,
    private val settlement: Settlement,
) {
    @PostMapping("/payments")
    fun create(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: AlphaCreatePayment,
    ): ResponseEntity<Any> {
        pause(properties.responseDelay)
        if (!authorized(authorization)) return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid API key")
        state.findByOrder(ID, request.orderId)?.let { return ResponseEntity.ok(it.toResponse()) }

        val scenario = state.scenarioFor(ID, request.orderId)
        if (scenario == Scenario.PROVIDER_ERROR) return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "Simulated provider error")

        val now = state.now()
        val payment =
            state.save(
                SimulatedPayment(
                    operator = ID,
                    paymentId = state.nextPaymentId("MA"),
                    orderId = request.orderId,
                    amount = request.amount,
                    msisdn = request.msisdn,
                    callbackUrl = request.callbackUrl,
                    scenario = scenario,
                    createdAt = now,
                    expiresAt = now.plus(if (scenario == Scenario.EXPIRE) SHORT_WINDOW else PAYMENT_WINDOW),
                ),
            )
        settlement.schedule(payment)
        if (scenario == Scenario.TIMEOUT) pause(properties.hangDuration)
        return ResponseEntity.status(HttpStatus.CREATED).body(payment.toResponse())
    }

    @GetMapping("/payments/by-order/{orderId}")
    fun lookup(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable orderId: String,
    ): ResponseEntity<Any> {
        pause(properties.responseDelay)
        if (!authorized(authorization)) return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid API key")
        val payment = state.findByOrder(ID, orderId) ?: return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Unknown order")
        return ResponseEntity.ok(settlement.expireIfDue(payment).toResponse())
    }

    private fun authorized(header: String?): Boolean {
        val expected = "Bearer ${properties.alpha.apiKey}"
        return header != null && MessageDigest.isEqual(header.toByteArray(), expected.toByteArray())
    }

    private fun error(
        status: HttpStatus,
        code: String,
        message: String,
    ): ResponseEntity<Any> = ResponseEntity.status(status).body(AlphaError(code, message))

    companion object {
        const val ID = "mock_alpha"
        private val PAYMENT_WINDOW: Duration = Duration.ofMinutes(15)
        private val SHORT_WINDOW: Duration = Duration.ofMinutes(1)
    }
}

internal fun SimulatedPayment.toResponse() =
    AlphaPayment(
        paymentId = paymentId,
        orderId = orderId,
        status = outcome?.wire ?: "PENDING",
        amount = reportedAmount ?: amount,
        reason = reason,
        approval = "PAYER_APPROVAL",
        expiresIn = if (outcome == null) Duration.between(createdAt, expiresAt).seconds else null,
    )

private fun pause(duration: Duration) {
    if (!duration.isZero) Thread.sleep(duration)
}
