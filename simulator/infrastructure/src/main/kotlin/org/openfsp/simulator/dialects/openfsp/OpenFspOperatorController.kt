package org.openfsp.simulator.dialects.openfsp

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.protocol.IdempotencyKeys
import org.openfsp.protocol.ProblemType
import org.openfsp.protocol.api.PaymentCollection
import org.openfsp.protocol.api.StatementCollection
import org.openfsp.simulator.configuration.SimulatorProperties
import org.openfsp.simulator.dialects.caller
import org.openfsp.simulator.operator.Caller
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.OperatorPayment
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.operator.api.OperatePayments
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeParseException

@RestController
@RequestMapping("/{operator}")
class OpenFspOperatorController(
    private val operations: OperatePayments,
    private val mapper: OpenFspMapper,
    private val requests: OpenFspRequests,
    private val idempotency: OpenFspIdempotency,
    private val keys: OpenFspWebhookKeys,
    private val properties: SimulatorProperties,
) {
    @GetMapping("/.well-known/openfsp-configuration")
    fun configuration(
        @PathVariable operator: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> {
        val profile = native(operator) ?: return ResponseEntity.notFound().build()
        OpenFspExchange(request, response)
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(CACHE)).body(mapper.toConfiguration(profile, keys.published(operator)))
    }

    @GetMapping("/v1/capabilities")
    fun capabilities(
        @PathVariable operator: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> {
        val profile = native(operator) ?: return ResponseEntity.notFound().build()
        val exchange = OpenFspExchange(request, response)
        if (!callerOf(operator, authorization).authenticated) return exchange.problem(ProblemType.UNAUTHENTICATED)
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(CACHE)).body(mapper.toCapabilities(profile))
    }

    @PostMapping("/v1/payments")
    fun create(
        @PathVariable operator: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestHeader(IDEMPOTENCY_KEY, required = false) key: String?,
        @RequestHeader(HttpHeaders.CONTENT_TYPE, required = false) contentType: String?,
        @RequestBody(required = false) body: ByteArray?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> {
        native(operator) ?: return ResponseEntity.notFound().build()
        val exchange = OpenFspExchange(request, response)
        val caller = callerOf(operator, authorization)
        if (!caller.authenticated) return exchange.problem(ProblemType.UNAUTHENTICATED)
        if (key == null) return exchange.problem(ProblemType.IDEMPOTENCY_KEY_REQUIRED)
        if (!IdempotencyKeys.isValid(key)) return exchange.problem(ProblemType.INVALID_FIELD, "The Idempotency-Key is not valid")
        return try {
            if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType))) throw OpenFspFailure(ProblemType.MALFORMED_REQUEST)
            val tree = requests.parse(body)
            val payment = requests.toPayment(operator, tree)
            val fingerprint = idempotency.fingerprint(tree)
            when (val start = idempotency.start(operator, key, fingerprint) { stillHeld(operator, it) }) {
                IdempotentStart.InProgress -> exchange.problem(ProblemType.IDEMPOTENCY_REQUEST_IN_PROGRESS).withRetryAfter()
                IdempotentStart.Reused -> exchange.problem(ProblemType.IDEMPOTENCY_KEY_REUSED)
                is IdempotentStart.Replay -> replay(start.response)
                IdempotentStart.Fresh -> created(operator, key, fingerprint, exchange) { operations.create(operator, caller, payment) }
            }
        } catch (failure: OpenFspFailure) {
            exchange.problem(failure)
        }
    }

    @GetMapping("/v1/payments/{paymentId}")
    fun read(
        @PathVariable operator: String,
        @PathVariable paymentId: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> = byId(operator, paymentId, authorization, OpenFspExchange(request, response))

    @PostMapping("/v1/payments/{paymentId}/synchronize")
    fun synchronize(
        @PathVariable operator: String,
        @PathVariable paymentId: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> = byId(operator, paymentId, authorization, OpenFspExchange(request, response))

    @GetMapping("/v1/payments")
    fun byReference(
        @PathVariable operator: String,
        @RequestParam(required = false) reference: String?,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> {
        native(operator) ?: return ResponseEntity.notFound().build()
        val exchange = OpenFspExchange(request, response)
        val caller = callerOf(operator, authorization)
        if (!caller.authenticated) return exchange.problem(ProblemType.UNAUTHENTICATED)
        if (reference.isNullOrBlank()) return exchange.problem(ProblemType.MISSING_FIELD, "The reference parameter is required")
        return when (val found = operations.lookup(operator, caller, reference)) {
            is OperatorResponse.Answered -> ResponseEntity.ok(PaymentCollection(listOf(resourceOf(operator, found.body))))
            is OperatorResponse.Refused -> if (found.status == NOT_FOUND) ResponseEntity.ok(PaymentCollection(emptyList())) else refused(exchange, found)
        }
    }

    @GetMapping("/v1/statements")
    fun statements(
        @PathVariable operator: String,
        @RequestParam(required = false) provider: String?,
        @RequestParam(name = "business_date", required = false) businessDate: String?,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> {
        native(operator) ?: return ResponseEntity.notFound().build()
        val exchange = OpenFspExchange(request, response)
        val caller = callerOf(operator, authorization)
        if (!caller.authenticated) return exchange.problem(ProblemType.UNAUTHENTICATED)
        if (provider.isNullOrBlank() || businessDate.isNullOrBlank()) return exchange.problem(ProblemType.MISSING_FIELD, "The provider and business_date parameters are required")
        val day =
            try {
                LocalDate.parse(businessDate)
            } catch (_: DateTimeParseException) {
                return exchange.problem(ProblemType.INVALID_FIELD, "business_date must be a full-date")
            }
        if (provider != operator) return ResponseEntity.ok(StatementCollection(emptyList()))
        return when (val statement = operations.statement(operator, caller, day)) {
            is OperatorResponse.Answered -> ResponseEntity.ok(StatementCollection(listOf(mapper.toStatement(operator, statement.body))))
            is OperatorResponse.Refused -> if (statement.status == NOT_FOUND) exchange.problem(ProblemType.CAPABILITY_NOT_SUPPORTED) else refused(exchange, statement)
        }
    }

    private fun byId(
        operator: String,
        paymentId: String,
        authorization: String?,
        exchange: OpenFspExchange,
    ): ResponseEntity<Any> {
        native(operator) ?: return ResponseEntity.notFound().build()
        val caller = callerOf(operator, authorization)
        if (!caller.authenticated) return exchange.problem(ProblemType.UNAUTHENTICATED)
        return when (val found = operations.lookupPayment(operator, caller, paymentId)) {
            is OperatorResponse.Answered -> ResponseEntity.ok(resourceOf(operator, found.body))
            is OperatorResponse.Refused -> refused(exchange, found)
        }
    }

    private fun created(
        operator: String,
        key: String,
        fingerprint: String,
        exchange: OpenFspExchange,
        create: () -> OperatorResponse<OperatorPayment>,
    ): ResponseEntity<Any> {
        val outcome =
            try {
                create()
            } catch (failure: RuntimeException) {
                idempotency.abandon(operator, key)
                throw failure
            }
        val recorded =
            when (outcome) {
                is OperatorResponse.Answered ->
                    if (outcome.status == CREATED) {
                        RecordedResponse(CREATED, resourceOf(operator, outcome.body), outcome.body.paymentId, mapper.location(operator, outcome.body.paymentId))
                    } else {
                        RecordedResponse(ProblemType.REFERENCE_CONFLICT.status, exchange.problem(ProblemType.REFERENCE_CONFLICT).body!!, null)
                    }
                is OperatorResponse.Refused -> RecordedResponse(outcome.status, refused(exchange, outcome).body!!, null).takeIf { outcome.status < SERVER_ERROR }
            }
        if (recorded == null) {
            idempotency.abandon(operator, key)
            return refused(exchange, outcome as OperatorResponse.Refused)
        }
        idempotency.finish(operator, key, fingerprint, recorded)
        return respond(recorded)
    }

    private fun replay(recorded: RecordedResponse): ResponseEntity<Any> =
        ResponseEntity
            .status(recorded.status)
            .header(IDEMPOTENT_REPLAY, "true")
            .apply { recorded.location?.let { location(URI.create(it)) } }
            .apply { if (recorded.status >= BAD_REQUEST) contentType(OpenFspExchange.PROBLEM) }
            .body(recorded.body)

    private fun respond(recorded: RecordedResponse): ResponseEntity<Any> =
        ResponseEntity
            .status(recorded.status)
            .apply { recorded.location?.let { location(URI.create(it)) } }
            .apply { if (recorded.status >= BAD_REQUEST) contentType(OpenFspExchange.PROBLEM) }
            .body(recorded.body)

    private fun stillHeld(
        operator: String,
        recorded: RecordedResponse,
    ): Boolean = recorded.paymentId == null || operations.checkout(operator, recorded.paymentId) != null

    private fun resourceOf(
        operator: String,
        payment: OperatorPayment,
    ) = mapper.toResource(operator, payment, operations.checkout(operator, payment.paymentId))

    private fun refused(
        exchange: OpenFspExchange,
        refusal: OperatorResponse.Refused,
    ): ResponseEntity<Any> = exchange.problem(PROBLEMS[refusal.status] ?: ProblemType.INTERNAL_ERROR, refusal.message)

    private fun ResponseEntity<Any>.withRetryAfter(): ResponseEntity<Any> = ResponseEntity.status(statusCode).headers(headers).header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS).body(body)

    private fun native(operator: String): OperatorProfile? = operations.profile(operator)?.takeIf { it.dialect == Dialect.OPENFSP }

    private fun callerOf(
        operator: String,
        authorization: String?,
    ): Caller = caller("Bearer ${properties.operator(operator).credential(API_KEY)}", authorization)

    companion object {
        const val API_KEY = "api-key"
        const val IDEMPOTENCY_KEY = "Idempotency-Key"
        const val IDEMPOTENT_REPLAY = "Idempotent-Replay"
        private const val CREATED = 201
        private const val BAD_REQUEST = 400
        private const val NOT_FOUND = 404
        private const val SERVER_ERROR = 500
        private const val RETRY_AFTER_SECONDS = "1"
        private val CACHE: Duration = Duration.ofMinutes(5)
        private val PROBLEMS =
            mapOf(
                400 to ProblemType.MALFORMED_REQUEST,
                401 to ProblemType.UNAUTHENTICATED,
                404 to ProblemType.NOT_FOUND,
                500 to ProblemType.INTERNAL_ERROR,
            )
    }
}
