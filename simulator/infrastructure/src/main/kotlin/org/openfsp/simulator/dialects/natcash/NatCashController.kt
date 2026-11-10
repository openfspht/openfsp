package org.openfsp.simulator.dialects.natcash

import org.openfsp.simulator.configuration.SimulatorProperties
import org.openfsp.simulator.dialects.DialectGuard
import org.openfsp.simulator.operator.Caller
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.operator.payment.api.OperatePayments
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/{operator}/api/online-payment")
class NatCashController(
    private val operations: OperatePayments,
    private val mapper: NatCashMapper,
    private val signatures: NatCashSignatures,
    private val guard: DialectGuard,
    private val properties: SimulatorProperties,
) {
    @PostMapping("/credential")
    fun requestPayment(
        @PathVariable operator: String,
        @RequestBody request: NatCashPaymentRequest,
    ): ResponseEntity<Any> {
        if (!guard.speaks(operator, Dialect.NATCASH)) return ResponseEntity.notFound().build()
        val caller = Caller(signatures.isAuthentic(properties.operator(operator), request), "${request.username}:${request.password}")
        return when (val response = operations.create(operator, caller, mapper.toDomain(request))) {
            is OperatorResponse.Answered -> ResponseEntity.ok(mapper.toCreated(response.body))
            is OperatorResponse.Refused ->
                if (response.status >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
                    ResponseEntity.status(response.status).build()
                } else {
                    ResponseEntity.ok(mapper.refused("ERR_${response.code}", response.message))
                }
        }
    }

    @PostMapping("/merchant/checkTransaction")
    fun checkTransaction(
        @PathVariable operator: String,
        @RequestBody query: NatCashTransactionQuery,
    ): ResponseEntity<Any> {
        if (!guard.speaks(operator, Dialect.NATCASH)) return ResponseEntity.notFound().build()
        val caller = Caller(signatures.isAuthentic(properties.operator(operator), query), "${query.username}:${query.password}")
        return when (val response = operations.lookup(operator, caller, query.orderNumber)) {
            is OperatorResponse.Answered -> ResponseEntity.ok(mapper.toTransaction(response.body))
            is OperatorResponse.Refused ->
                if (response.status == HttpStatus.NOT_FOUND.value()) ResponseEntity.ok(mapper.notFound()) else ResponseEntity.ok(mapper.queryRefused(response.message))
        }
    }
}
