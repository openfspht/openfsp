package org.openfsp.simulator.dialects.openfsp

import org.openfsp.simulator.configuration.SimulatorProperties
import org.openfsp.simulator.dialects.DialectGuard
import org.openfsp.simulator.dialects.caller
import org.openfsp.simulator.dialects.toEntity
import org.openfsp.simulator.operator.Caller
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.OperatorPayment
import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.operator.api.OperatePayments
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/{operator}/v1")
class OpenFspOperatorController(
    private val operations: OperatePayments,
    private val mapper: OpenFspMapper,
    private val guard: DialectGuard,
    private val properties: SimulatorProperties,
) {
    @PostMapping("/payments")
    fun create(
        @PathVariable operator: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestBody request: OpenFspPaymentRequest,
    ): ResponseEntity<Any> = payment(operator) { operations.create(operator, callerOf(operator, authorization), mapper.toDomain(request)) }

    @GetMapping("/payments/by-order/{orderId}")
    fun lookup(
        @PathVariable operator: String,
        @PathVariable orderId: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
    ): ResponseEntity<Any> = payment(operator) { operations.lookup(operator, callerOf(operator, authorization), orderId) }

    @GetMapping("/statements/{day}")
    fun statement(
        @PathVariable operator: String,
        @PathVariable day: LocalDate,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
    ): ResponseEntity<Any> {
        if (!guard.speaks(operator, Dialect.OPENFSP)) return ResponseEntity.notFound().build()
        return operations.statement(operator, callerOf(operator, authorization), day).toEntity({ status, body -> ResponseEntity.status(status).body(body) }, ::refused)
    }

    private fun payment(
        operator: String,
        call: () -> OperatorResponse<OperatorPayment>,
    ): ResponseEntity<Any> {
        if (!guard.speaks(operator, Dialect.OPENFSP)) return ResponseEntity.notFound().build()
        return call().toEntity({ status, body -> ResponseEntity.status(status).body(mapper.toWire(body)) }, ::refused)
    }

    private fun callerOf(
        operator: String,
        authorization: String?,
    ): Caller = caller("Bearer ${properties.operator(operator).credential(API_KEY)}", authorization)

    private fun refused(refusal: OperatorResponse.Refused): ResponseEntity<Any> = ResponseEntity.status(refusal.status).body(OpenFspError(refusal.code, refusal.message))

    companion object {
        const val API_KEY = "api-key"
    }
}
