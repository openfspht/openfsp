package org.openfsp.simulator.dialects.moncash

import org.openfsp.server.Digests
import org.openfsp.simulator.dialects.DialectGuard
import org.openfsp.simulator.operator.Caller
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.payment.OperatorPayment
import org.openfsp.simulator.payment.api.OperatePayments
import org.openfsp.simulator.shared.configuration.SimulatorProperties
import org.springframework.http.HttpHeaders
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
import java.util.Base64

@RestController
class MonCashController(
    private val operations: OperatePayments,
    private val mapper: MonCashMapper,
    private val tokens: MonCashTokens,
    private val guard: DialectGuard,
    private val properties: SimulatorProperties,
) {
    @PostMapping("/{operator}/Api/oauth/token")
    fun token(
        @PathVariable operator: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestParam("grant_type") grantType: String,
    ): ResponseEntity<Any> {
        if (!guard.speaks(operator, Dialect.MONCASH)) return ResponseEntity.notFound().build()
        val settings = properties.operator(operator)
        val expected = "Basic " + Base64.getEncoder().encodeToString("${settings.credential(CLIENT_ID)}:${settings.credential(CLIENT_SECRET)}".toByteArray())
        if (grantType != CLIENT_CREDENTIALS || !Digests.secretEquals(expected, authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "unauthorized", "error_description" to "Bad credentials"))
        }
        return ResponseEntity.ok(MonCashToken(tokens.issue(operator), "bearer", MonCashTokens.LIFETIME.seconds, SCOPE))
    }

    @PostMapping("/{operator}/Api/v1/CreatePayment")
    fun create(
        @PathVariable operator: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestBody request: MonCashCreatePayment,
    ): ResponseEntity<Any> {
        if (!guard.speaks(operator, Dialect.MONCASH)) return ResponseEntity.notFound().build()
        return when (val response = operations.create(operator, callerOf(operator, authorization), mapper.toDomain(request))) {
            is OperatorResponse.Answered -> ResponseEntity.status(HttpStatus.ACCEPTED).body(mapper.toCreated(response.body))
            is OperatorResponse.Refused -> ResponseEntity.status(response.status).body(mapper.toError(MonCashMapper.CREATE_PATH, response))
        }
    }

    @PostMapping("/{operator}/Api/v1/RetrieveOrderPayment")
    fun retrieveOrder(
        @PathVariable operator: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestBody query: MonCashOrderQuery,
    ): ResponseEntity<Any> = retrieve(operator, RETRIEVE_ORDER) { operations.lookup(operator, callerOf(operator, authorization), query.orderId) }

    @PostMapping("/{operator}/Api/v1/RetrieveTransactionPayment")
    fun retrieveTransaction(
        @PathVariable operator: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestBody query: MonCashTransactionQuery,
    ): ResponseEntity<Any> = retrieve(operator, RETRIEVE_TRANSACTION) { operations.lookupPayment(operator, callerOf(operator, authorization), query.transactionId) }

    @GetMapping("/{operator}/Moncash-middleware/Payment/Redirect")
    fun redirect(
        @PathVariable operator: String,
        @RequestParam token: String,
    ): ResponseEntity<Void> {
        if (!guard.speaks(operator, Dialect.MONCASH)) return ResponseEntity.notFound().build()
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/$operator/checkout/$token")).build()
    }

    private fun retrieve(
        operator: String,
        path: String,
        call: () -> OperatorResponse<OperatorPayment>,
    ): ResponseEntity<Any> {
        if (!guard.speaks(operator, Dialect.MONCASH)) return ResponseEntity.notFound().build()
        return when (val response = call()) {
            is OperatorResponse.Answered -> mapper.toResult(path, response.body)?.let { ResponseEntity.ok(it) } ?: notFound(path)
            is OperatorResponse.Refused -> if (response.status == HttpStatus.NOT_FOUND.value()) notFound(path) else ResponseEntity.status(response.status).body(mapper.toError(path, response))
        }
    }

    private fun notFound(path: String): ResponseEntity<Any> = ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapper.notFound(path))

    private fun callerOf(
        operator: String,
        authorization: String?,
    ) = Caller.ofPublishedAccount(authenticated = tokens.isValid(operator, authorization))

    companion object {
        const val CLIENT_ID = "client-id"
        const val CLIENT_SECRET = "client-secret"
        private const val CLIENT_CREDENTIALS = "client_credentials"
        private const val SCOPE = "read,write"
        private const val RETRIEVE_ORDER = "/v1/RetrieveOrderPayment"
        private const val RETRIEVE_TRANSACTION = "/v1/RetrieveTransactionPayment"
    }
}
