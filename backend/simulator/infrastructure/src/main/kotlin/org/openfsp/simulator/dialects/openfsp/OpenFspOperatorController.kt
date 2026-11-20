package org.openfsp.simulator.dialects.openfsp

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.protocol.IdempotencyKeys
import org.openfsp.protocol.ProblemType
import org.openfsp.protocol.StatementPeriod
import org.openfsp.protocol.api.PaymentCollection
import org.openfsp.protocol.api.StatementCollection
import org.openfsp.server.idempotency.RequestFingerprints
import org.openfsp.simulator.emission.RunTerms
import org.openfsp.simulator.emission.api.OpenRuns
import org.openfsp.simulator.idempotency.CreationAttempt
import org.openfsp.simulator.idempotency.IdempotencyScope
import org.openfsp.simulator.idempotency.IdempotentCreation
import org.openfsp.simulator.idempotency.RecordedResponse
import org.openfsp.simulator.idempotency.api.CreateIdempotently
import org.openfsp.simulator.operator.AmountLimits
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.payment.OperatorPayment
import org.openfsp.simulator.payment.api.OperatePayments
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
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

@RestController
@RequestMapping("/{operator}")
class OpenFspOperatorController(
    private val operations: OperatePayments,
    private val mapper: OpenFspMapper,
    private val requests: OpenFspRequests,
    private val creations: CreateIdempotently,
    private val keys: OpenFspWebhookKeys,
    private val principals: OpenFspPrincipals,
    private val runs: OpenRuns,
    private val terms: RunTerms,
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

    @PostMapping(OpenFspMapper.CONFORMANCE)
    fun conformance(
        @PathVariable operator: String,
        @RequestBody(required = false) body: ByteArray?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> {
        val profile = native(operator)?.takeIf(mapper::preparesConformance) ?: return ResponseEntity.notFound().build()
        val exchange = OpenFspExchange(request, response)
        val webhookUrl =
            try {
                requests.webhookUrlOf(requests.parse(body))
            } catch (failure: OpenFspException) {
                return exchange.problem(failure)
            }
        val run =
            runs.open(operator, principals.issueKey(), principals.issueKey(), webhookUrl, terms)
                ?: return exchange.problem(ProblemType.RATE_LIMITED, "The simulator holds as many conformance runs as it admits").withRetryAfter(RUN_RETRY_AFTER_SECONDS)
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(mapper.toFixtures(profile, run))
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
        if (!principals.callerOf(operator, authorization).authenticated) return exchange.problem(ProblemType.UNAUTHENTICATED)
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
        val profile = native(operator) ?: return ResponseEntity.notFound().build()
        val exchange = OpenFspExchange(request, response)
        val caller = principals.callerOf(operator, authorization)
        if (!caller.authenticated) return exchange.problem(ProblemType.UNAUTHENTICATED)
        if (key == null) return exchange.problem(ProblemType.IDEMPOTENCY_KEY_REQUIRED)
        if (!IdempotencyKeys.isValid(key)) return exchange.problem(ProblemType.INVALID_FIELD, "The Idempotency-Key is not valid")
        return try {
            if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType))) throw OpenFspException(ProblemType.MALFORMED_REQUEST)
            val tree = requests.parse(body)
            val scope = IdempotencyScope(operator, caller.account, caller.principal.name, key)
            val creation = creations.create(scope, RequestFingerprints.of(tree)) { attempt(operator, exchange) { operations.create(operator, caller, requests.toPayment(operator, tree, profile.callbacks)) } }
            when (creation) {
                IdempotentCreation.InProgress -> exchange.problem(ProblemType.IDEMPOTENCY_REQUEST_IN_PROGRESS).withRetryAfter()
                IdempotentCreation.Reused -> exchange.problem(ProblemType.IDEMPOTENCY_KEY_REUSED)
                is IdempotentCreation.Replayed -> replay(creation.response)
                is IdempotentCreation.Answered -> respond(creation.response)
            }
        } catch (failure: OpenFspException) {
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
        val caller = principals.callerOf(operator, authorization)
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
        @RequestParam(name = StatementPeriod.BUSINESS_DATE, required = false) businessDate: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) limit: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> {
        native(operator) ?: return ResponseEntity.notFound().build()
        val exchange = OpenFspExchange(request, response)
        val caller = principals.callerOf(operator, authorization)
        operations.statementAccess(operator, caller)?.let { return refused(exchange, it) }
        if (provider.isNullOrBlank()) return exchange.problem(ProblemType.MISSING_FIELD, "The provider parameter is required")
        val period =
            when (val requested = StatementPeriod.of(businessDate, from, to)) {
                is StatementPeriod.Request.Accepted -> requested.period
                is StatementPeriod.Request.Missing -> return exchange.problem(ProblemType.MISSING_FIELD, "A period is required: business_date, or from and to")
                is StatementPeriod.Request.Invalid -> return exchange.problem(ProblemType.INVALID_FIELD, "${requested.parameter} ${requested.detail}")
            }
        val size = limit?.toIntOrNull()?.takeIf { it in PAGE_SIZES } ?: if (limit == null) StatementPeriod.DEFAULT_LIMIT else return exchange.problem(ProblemType.INVALID_FIELD, "limit must be an integer from 1 to 100")
        val offset = cursor?.toIntOrNull()?.takeIf { it >= 0 } ?: if (cursor == null) 0 else return exchange.problem(ProblemType.INVALID_FIELD, "cursor is not a cursor of this endpoint")
        return when (val page = operations.statement(operator, caller, provider, period.from, period.to, offset, size)) {
            is OperatorResponse.Answered -> ResponseEntity.ok(StatementCollection(page.body.payments.map(mapper::toEntry), page.body.nextOffset?.toString()))
            is OperatorResponse.Refused -> refused(exchange, page)
        }
    }

    private fun byId(
        operator: String,
        paymentId: String,
        authorization: String?,
        exchange: OpenFspExchange,
    ): ResponseEntity<Any> {
        native(operator) ?: return ResponseEntity.notFound().build()
        val caller = principals.callerOf(operator, authorization)
        if (!caller.authenticated) return exchange.problem(ProblemType.UNAUTHENTICATED)
        return when (val found = operations.lookupPayment(operator, caller, paymentId)) {
            is OperatorResponse.Answered -> ResponseEntity.ok(resourceOf(operator, found.body))
            is OperatorResponse.Refused -> refused(exchange, found)
        }
    }

    private fun attempt(
        operator: String,
        exchange: OpenFspExchange,
        create: () -> OperatorResponse<OperatorPayment>,
    ): CreationAttempt {
        val outcome =
            try {
                create()
            } catch (failure: OpenFspException) {
                if (failure.type !in DETERMINED) throw failure
                return CreationAttempt.Determined(RecordedResponse(failure.type.status, exchange.problem(failure).body!!, null))
            }
        return when (outcome) {
            is OperatorResponse.Answered ->
                if (outcome.status == CREATED) {
                    CreationAttempt.Determined(RecordedResponse(CREATED, resourceOf(operator, outcome.body), outcome.body.paymentId, mapper.location(operator, outcome.body.paymentId)))
                } else {
                    CreationAttempt.Determined(RecordedResponse(ProblemType.REFERENCE_CONFLICT.status, exchange.problem(ProblemType.REFERENCE_CONFLICT).body!!, null))
                }
            is OperatorResponse.Refused -> {
                val problem = refusedCreation(operator, exchange, outcome)
                val response = RecordedResponse(problem.statusCode.value(), problem.body!!, null)
                if (outcome.status < SERVER_ERROR) CreationAttempt.Determined(response) else CreationAttempt.Undetermined(response)
            }
        }
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

    private fun resourceOf(
        operator: String,
        payment: OperatorPayment,
    ) = mapper.toResource(operator, payment, operations.checkout(operator, payment.paymentId))

    private fun refusedCreation(
        operator: String,
        exchange: OpenFspExchange,
        refusal: OperatorResponse.Refused,
    ): ResponseEntity<Any> {
        val limits = operations.profile(operator)?.limits?.takeIf { refusal.code == AmountLimits.OUT_OF_RANGE } ?: return refused(exchange, refusal)
        return exchange.problem(ProblemType.AMOUNT_OUT_OF_RANGE, refusal.message, extensions = mapper.toLimitMembers(limits))
    }

    private fun refused(
        exchange: OpenFspExchange,
        refusal: OperatorResponse.Refused,
    ): ResponseEntity<Any> = exchange.problem(CODES[refusal.code] ?: PROBLEMS[refusal.status] ?: ProblemType.INTERNAL_ERROR, refusal.message)

    private fun ResponseEntity<Any>.withRetryAfter(seconds: String = RETRY_AFTER_SECONDS): ResponseEntity<Any> = ResponseEntity.status(statusCode).headers(headers).header(HttpHeaders.RETRY_AFTER, seconds).body(body)

    private fun native(operator: String): OperatorProfile? = operations.profile(operator)?.takeIf { it.dialect == Dialect.OPENFSP }

    companion object {
        const val IDEMPOTENCY_KEY = "Idempotency-Key"
        const val IDEMPOTENT_REPLAY = "Idempotent-Replay"
        private const val CREATED = 201
        private const val BAD_REQUEST = 400
        private const val NOT_FOUND = 404
        private const val SERVER_ERROR = 500
        private const val RETRY_AFTER_SECONDS = "1"
        private const val RUN_RETRY_AFTER_SECONDS = "60"
        private val CACHE: Duration = Duration.ofMinutes(5)
        private val PAGE_SIZES = StatementPeriod.MIN_LIMIT..StatementPeriod.MAX_LIMIT
        private val DETERMINED = setOf(ProblemType.MALFORMED_REQUEST, ProblemType.UNKNOWN_FIELD, ProblemType.MISSING_FIELD, ProblemType.INVALID_FIELD, ProblemType.CAPABILITY_NOT_SUPPORTED)
        private val CODES = mapOf("CAPABILITY_NOT_SUPPORTED" to ProblemType.CAPABILITY_NOT_SUPPORTED)
        private val PROBLEMS =
            mapOf(
                400 to ProblemType.MALFORMED_REQUEST,
                401 to ProblemType.UNAUTHENTICATED,
                403 to ProblemType.FORBIDDEN,
                404 to ProblemType.NOT_FOUND,
                500 to ProblemType.PROVIDER_ERROR,
                504 to ProblemType.PROVIDER_TIMEOUT,
            )
    }
}
