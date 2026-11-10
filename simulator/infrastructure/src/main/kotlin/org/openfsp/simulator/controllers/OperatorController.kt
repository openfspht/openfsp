package org.openfsp.simulator.controllers

import org.openfsp.simulator.operator.CreatePaymentAtOperator
import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.operator.api.OperatePayments
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.LocalDate

data class OperatorError(
    val code: String,
    val message: String,
)

@RestController
class OperatorController(
    private val operations: OperatePayments,
) {
    @PostMapping("/{operator}/v1/payments")
    fun create(
        @PathVariable operator: String,
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: CreatePaymentAtOperator,
    ): ResponseEntity<Any> = respond(operations.create(operator, authorization, request))

    @GetMapping("/{operator}/v1/payments/by-order/{orderId}")
    fun lookup(
        @PathVariable operator: String,
        @PathVariable orderId: String,
        @RequestHeader("Authorization", required = false) authorization: String?,
    ): ResponseEntity<Any> = respond(operations.lookup(operator, authorization, orderId))

    @GetMapping("/{operator}/v1/statements/{day}")
    fun statement(
        @PathVariable operator: String,
        @PathVariable day: LocalDate,
        @RequestHeader("Authorization", required = false) authorization: String?,
    ): ResponseEntity<Any> = respond(operations.statement(operator, authorization, day))

    @GetMapping("/{operator}/checkout/{paymentId}", produces = ["text/html"])
    fun checkout(
        @PathVariable operator: String,
        @PathVariable paymentId: String,
    ): ResponseEntity<String> {
        val payment = operations.checkout(operator, paymentId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(CheckoutPage.render(operator, payment))
    }

    @PostMapping("/{operator}/checkout/{paymentId}")
    fun pay(
        @PathVariable operator: String,
        @PathVariable paymentId: String,
        @RequestParam decision: String,
    ): ResponseEntity<Void> {
        if (operations.checkout(operator, paymentId) == null) return ResponseEntity.notFound().build()
        val target = operations.pay(operator, paymentId, decision == "pay") ?: return ResponseEntity.noContent().build()
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(target)).build()
    }

    private fun respond(response: OperatorResponse<Any>): ResponseEntity<Any> =
        when (response) {
            is OperatorResponse.Answered -> ResponseEntity.status(response.status).body(response.body)
            is OperatorResponse.Refused -> ResponseEntity.status(response.status).body(OperatorError(response.code, response.message))
        }
}
