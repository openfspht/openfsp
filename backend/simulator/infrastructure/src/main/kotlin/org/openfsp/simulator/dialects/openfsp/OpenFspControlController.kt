package org.openfsp.simulator.dialects.openfsp

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.ProblemType
import org.openfsp.protocol.api.ControlledPaymentResource
import org.openfsp.simulator.control.Declaration
import org.openfsp.simulator.control.api.ControlSimulation
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.Principal
import org.openfsp.simulator.payment.Outcome
import org.openfsp.simulator.payment.api.OperatePayments
import org.openfsp.simulator.scenario.Scenario
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/{operator}${OpenFspMapper.CONTROL}")
class OpenFspControlController(
    private val control: ControlSimulation,
    private val operations: OperatePayments,
    private val principals: OpenFspPrincipals,
) {
    @PutMapping("/references/{reference}/scenario")
    fun declare(
        @PathVariable operator: String,
        @PathVariable reference: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestBody body: Map<String, Any?>,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> =
        controlling(operator, authorization, request, response) { exchange ->
            val scenario = Scenario.entries.firstOrNull { it.name == body[SCENARIO] } ?: return@controlling exchange.problem(ProblemType.INVALID_FIELD, "scenario is not a scenario of the control interface")
            when (control.declareByReference(operator, reference, scenario)) {
                Declaration.DECLARED -> done()
                else -> exchange.problem(ProblemType.INVALID_FIELD, "$operator cannot produce the scenario ${scenario.name}")
            }
        }

    @GetMapping("/references/{reference}")
    fun state(
        @PathVariable operator: String,
        @PathVariable reference: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> =
        controlling(operator, authorization, request, response) { exchange ->
            val payment = control.paymentByReference(operator, reference) ?: return@controlling exchange.problem(ProblemType.NOT_FOUND, "No payment exists for this reference")
            ResponseEntity.ok(ControlledPaymentResource(payment.paymentId, payment.isSettled, payment.callbacksSent))
        }

    @PostMapping("/payments/{paymentId}/resolve")
    fun resolve(
        @PathVariable operator: String,
        @PathVariable paymentId: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestBody body: Map<String, Any?>,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> =
        controlling(operator, authorization, request, response) { exchange ->
            operations.checkout(operator, paymentId) ?: return@controlling exchange.problem(ProblemType.NOT_FOUND)
            val outcome = OUTCOMES[body[OUTCOME]] ?: return@controlling exchange.problem(ProblemType.INVALID_FIELD, "outcome must be one of ${OUTCOMES.keys.joinToString()}")
            val reason = body[REASON]?.let { given -> FailureReason.entries.firstOrNull { it.wire == given } ?: return@controlling exchange.problem(ProblemType.INVALID_FIELD, "reason is not a failure reason") }
            control.resolve(paymentId, outcome, reason?.name.takeIf { outcome == Outcome.FAILED })
            done()
        }

    @PostMapping("/payments/{paymentId}/callback")
    fun callback(
        @PathVariable operator: String,
        @PathVariable paymentId: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> =
        controlling(operator, authorization, request, response) { exchange ->
            operations.checkout(operator, paymentId) ?: return@controlling exchange.problem(ProblemType.NOT_FOUND)
            control.deliverCallback(paymentId)
            done()
        }

    private fun controlling(
        operator: String,
        authorization: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
        act: (OpenFspExchange) -> ResponseEntity<Any>,
    ): ResponseEntity<Any> {
        if (operations.profile(operator)?.dialect != Dialect.OPENFSP) return ResponseEntity.notFound().build()
        val exchange = OpenFspExchange(request, response)
        val caller = principals.callerOf(operator, authorization)
        if (!caller.authenticated || caller.principal != Principal.PRIMARY) return exchange.problem(ProblemType.UNAUTHENTICATED)
        return act(exchange)
    }

    private fun done(): ResponseEntity<Any> = ResponseEntity.noContent().build()

    private companion object {
        const val SCENARIO = "scenario"
        const val OUTCOME = "outcome"
        const val REASON = "reason"
        val OUTCOMES =
            mapOf(
                PaymentStatus.SUCCEEDED.wire to Outcome.SUCCESSFUL,
                PaymentStatus.FAILED.wire to Outcome.FAILED,
                PaymentStatus.EXPIRED.wire to Outcome.EXPIRED,
            )
    }
}
