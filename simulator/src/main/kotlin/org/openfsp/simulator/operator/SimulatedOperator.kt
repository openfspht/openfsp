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
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDate

data class OperatorCreatePayment(
    val orderId: String,
    val amount: String,
    val msisdn: String?,
    val description: String?,
    val callbackUrl: String?,
)

data class OperatorPayment(
    val paymentId: String,
    val orderId: String,
    val status: String,
    val amount: String,
    val reason: String?,
    val approval: String,
    val expiresIn: Long?,
)

data class OperatorError(
    val code: String,
    val message: String,
)

@RestController
class SimulatedOperator(
    private val state: SimulatorState,
    private val properties: SimulatorProperties,
    private val settlement: Settlement,
) {
    @PostMapping("/{operator}/v1/payments")
    fun create(
        @PathVariable operator: String,
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: OperatorCreatePayment,
    ): ResponseEntity<Any> {
        pause(properties.responseDelay)
        val profile = properties.operators[operator] ?: return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Unknown operator")
        if (!authorized(profile, authorization)) return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid API key")
        val scenario = state.scenarioFor(operator, request.orderId)
        if (scenario == Scenario.CREDENTIAL_ECHO) return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Rejected request authorized with $authorization")
        if (profile.idempotentByOrder) state.findByOrder(operator, request.orderId)?.let { return ResponseEntity.ok(it.toResponse()) }
        if (scenario == Scenario.PROVIDER_ERROR) return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "Simulated provider error")

        val now = state.now()
        val payment =
            state.save(
                SimulatedPayment(
                    operator = operator,
                    paymentId = state.nextPaymentId(profile.referencePrefix),
                    orderId = request.orderId,
                    amount = request.amount,
                    msisdn = request.msisdn,
                    callbackUrl = request.callbackUrl.takeIf { profile.callbacks },
                    scenario = scenario,
                    createdAt = now,
                    expiresAt = now.plus(if (scenario == Scenario.EXPIRE) SHORT_WINDOW else PAYMENT_WINDOW),
                ),
            )
        settlement.schedule(payment)
        if (scenario in HANGING) pause(properties.hangDuration)
        if (scenario == Scenario.TIMEOUT_THEN_NOTHING) state.forget(payment.paymentId)
        return ResponseEntity.status(HttpStatus.CREATED).body(payment.toResponse())
    }

    @GetMapping("/{operator}/v1/payments/by-order/{orderId}")
    fun lookup(
        @PathVariable operator: String,
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable orderId: String,
    ): ResponseEntity<Any> {
        pause(properties.responseDelay)
        val profile = properties.operators[operator]?.takeIf { it.lookup } ?: return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Unknown endpoint")
        if (!authorized(profile, authorization)) return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid API key")
        val payment = state.findByOrder(operator, orderId) ?: return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Unknown order")
        val looked = state.update(payment.paymentId) { it.copy(lookups = it.lookups + 1) }!!
        return ResponseEntity.ok(settlement.expireIfDue(looked).toLookupResponse())
    }

    @GetMapping("/{operator}/v1/statements/{day}")
    fun statement(
        @PathVariable operator: String,
        @PathVariable day: LocalDate,
        @RequestHeader("Authorization", required = false) authorization: String?,
    ): ResponseEntity<Any> {
        pause(properties.responseDelay)
        val profile = properties.operators[operator]?.takeIf { it.statements } ?: return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Unknown endpoint")
        if (!authorized(profile, authorization)) return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid API key")
        return ResponseEntity.ok(state.statement(operator, day))
    }

    private fun authorized(
        profile: SimulatorProperties.OperatorProfile,
        header: String?,
    ): Boolean = header != null && MessageDigest.isEqual(header.toByteArray(), "Bearer ${profile.apiKey}".toByteArray())

    private fun error(
        status: HttpStatus,
        code: String,
        message: String,
    ): ResponseEntity<Any> = ResponseEntity.status(status).body(OperatorError(code, message))

    private companion object {
        val PAYMENT_WINDOW: Duration = Duration.ofMinutes(15)
        val SHORT_WINDOW: Duration = Duration.ofMinutes(1)
        val HANGING = setOf(Scenario.TIMEOUT, Scenario.TIMEOUT_THEN_SUCCESS, Scenario.TIMEOUT_THEN_NOTHING)
    }
}

internal fun SimulatedPayment.toResponse() =
    OperatorPayment(
        paymentId = paymentId,
        orderId = orderId,
        status = outcome?.wire ?: "PENDING",
        amount = reportedAmount ?: amount,
        reason = reason,
        approval = "PAYER_APPROVAL",
        expiresIn = if (outcome == null) Duration.between(createdAt, expiresAt).seconds else null,
    )

internal fun SimulatedPayment.toLookupResponse(): OperatorPayment =
    when {
        scenario == Scenario.UNKNOWN_STATUS && outcome != null -> toResponse().copy(status = "IN_REVIEW")
        scenario == Scenario.CONTRADICT && outcome == Outcome.SUCCESSFUL && lookups > 1 -> toResponse().copy(status = "FAILED", reason = "DECLINED")
        else -> toResponse()
    }

private fun pause(duration: Duration) {
    if (!duration.isZero) Thread.sleep(duration)
}
