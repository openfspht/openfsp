package org.openfsp.simulator.dialects.openfsp

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.protocol.IdempotencyKeys
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.ProblemType
import org.openfsp.protocol.api.PaymentCollection
import org.openfsp.protocol.api.StatementCollection
import org.openfsp.protocol.api.StatementEntryResource
import org.openfsp.simulator.configuration.SimulatorProperties
import org.openfsp.simulator.dialects.caller
import org.openfsp.simulator.operator.Caller
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.operator.control.api.ControlSimulation
import org.openfsp.simulator.operator.payment.OperatorPayment
import org.openfsp.simulator.operator.payment.api.OperatePayments
import org.openfsp.simulator.operator.space.SpaceId
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

@JsonInclude(JsonInclude.Include.NON_NULL)
data class StatementEntryPage(
    val data: List<StatementEntryResource>,
    @JsonProperty("next_cursor") val nextCursor: String? = null,
)

@RestController
@RequestMapping("/{operator}")
class OpenFspOperatorController(
    private val operations: OperatePayments,
    private val control: ControlSimulation,
    private val mapper: OpenFspMapper,
    private val requests: OpenFspRequests,
    private val idempotency: OpenFspIdempotency,
    private val keys: OpenFspWebhookKeys,
    private val properties: SimulatorProperties,
) {
    @GetMapping("/.well-known/openfsp-configuration")
    fun configuration(
        space: SpaceId,
        @PathVariable operator: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> {
        val profile = native(operator) ?: return ResponseEntity.notFound().build()
        OpenFspExchange(request, response)
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(CACHE)).body(mapper.toConfiguration(space, profile, keys.published(operator)))
    }

    @GetMapping("/v1/capabilities")
    fun capabilities(
        space: SpaceId,
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
        space: SpaceId,
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
        val principal = principalOf(operator, authorization) ?: return exchange.problem(ProblemType.UNAUTHENTICATED)
        val caller = Caller(authenticated = true, credential = authorization.orEmpty())
        if (key == null) return exchange.problem(ProblemType.IDEMPOTENCY_KEY_REQUIRED)
        if (!IdempotencyKeys.isValid(key)) return exchange.problem(ProblemType.INVALID_FIELD, "The Idempotency-Key is not valid")
        return try {
            if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType))) throw OpenFspFailure(ProblemType.MALFORMED_REQUEST)
            val tree = requests.parse(body)
            val fingerprint = idempotency.fingerprint(tree)
            val scope = IdempotencyScope(space, operator, principal, key)
            when (val start = idempotency.start(scope, fingerprint) { stillHeld(space, operator, it) }) {
                IdempotentStart.InProgress -> exchange.problem(ProblemType.IDEMPOTENCY_REQUEST_IN_PROGRESS).withRetryAfter()
                IdempotentStart.Reused -> exchange.problem(ProblemType.IDEMPOTENCY_KEY_REUSED)
                is IdempotentStart.Replay -> replay(start.response, exchange)
                IdempotentStart.Fresh -> created(space, operator, scope, fingerprint, exchange) { operations.create(space, operator, caller, requests.toPayment(operator, tree)) }
            }
        } catch (failure: OpenFspFailure) {
            exchange.problem(failure)
        }
    }

    @GetMapping("/v1/payments/{paymentId}")
    fun read(
        space: SpaceId,
        @PathVariable operator: String,
        @PathVariable paymentId: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> = byId(space, operator, paymentId, authorization, OpenFspExchange(request, response), synchronizing = false)

    @PostMapping("/v1/payments/{paymentId}/synchronize")
    fun synchronize(
        space: SpaceId,
        @PathVariable operator: String,
        @PathVariable paymentId: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> = byId(space, operator, paymentId, authorization, OpenFspExchange(request, response), synchronizing = true)

    @GetMapping("/v1/payments")
    fun byReference(
        space: SpaceId,
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
        return when (val found = operations.lookup(space, operator, caller, reference)) {
            is OperatorResponse.Answered -> ResponseEntity.ok(PaymentCollection(listOf(resourceOf(space, operator, found.body))))
            is OperatorResponse.Refused -> if (found.status == NOT_FOUND) ResponseEntity.ok(PaymentCollection(emptyList())) else refused(exchange, found)
        }
    }

    @GetMapping("/v1/statements")
    fun statements(
        space: SpaceId,
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
        if (principalOf(operator, authorization) != API_KEY) return exchange.problem(ProblemType.FORBIDDEN, STATEMENTS_SCOPE)
        if (provider.isNullOrBlank() || businessDate.isNullOrBlank()) return exchange.problem(ProblemType.MISSING_FIELD, "The provider and business_date parameters are required")
        val day =
            try {
                LocalDate.parse(businessDate)
            } catch (_: DateTimeParseException) {
                return exchange.problem(ProblemType.INVALID_FIELD, "business_date must be a full-date")
            }
        if (provider != operator) return ResponseEntity.ok(StatementCollection(emptyList()))
        return when (val statement = operations.statement(space, operator, caller, day)) {
            is OperatorResponse.Answered -> ResponseEntity.ok(StatementCollection(listOf(mapper.toStatement(operator, statement.body))))
            is OperatorResponse.Refused -> if (statement.status == NOT_FOUND) ResponseEntity.ok(StatementCollection(emptyList())) else refused(exchange, statement)
        }
    }

    @GetMapping("/v1/statement_entries")
    fun statementEntries(
        space: SpaceId,
        @PathVariable operator: String,
        @RequestParam(required = false) match: String?,
        @RequestParam(required = false) provider: String?,
        @RequestParam(required = false) limit: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> {
        native(operator) ?: return ResponseEntity.notFound().build()
        val exchange = OpenFspExchange(request, response)
        val caller = callerOf(operator, authorization)
        if (!caller.authenticated) return exchange.problem(ProblemType.UNAUTHENTICATED)
        if (principalOf(operator, authorization) != API_KEY) return exchange.problem(ProblemType.FORBIDDEN, STATEMENTS_SCOPE)
        if (match.isNullOrBlank()) return exchange.problem(ProblemType.MISSING_FIELD, "The match parameter is required")
        if (match !in ENTRY_MATCHES) return exchange.problem(ProblemType.INVALID_FIELD, "match must be unmatched or conflict")
        val size = limit?.toIntOrNull()?.takeIf { it in PAGE_SIZES } ?: if (limit == null) DEFAULT_PAGE_SIZE else return exchange.problem(ProblemType.INVALID_FIELD, "limit must be an integer from 1 to 100")
        val offset = cursor?.toIntOrNull()?.takeIf { it >= 0 } ?: if (cursor == null) 0 else return exchange.problem(ProblemType.INVALID_FIELD, "cursor is not a cursor of this endpoint")
        if (provider != null && provider != operator) return ResponseEntity.ok(StatementEntryPage(emptyList()))
        val days = control.statementDays(space, operator).let { (it.closed + it.today).distinct().sorted() }
        val entries =
            days
                .flatMap { day ->
                    when (val statement = operations.statement(space, operator, caller, day)) {
                        is OperatorResponse.Answered -> mapper.toStatement(operator, statement.body).entries
                        is OperatorResponse.Refused -> emptyList()
                    }
                }.filter { it.match == match }
                .sortedBy { it.bookedAt }
        val page = entries.drop(offset).take(size)
        val next = (offset + size).takeIf { it < entries.size }?.toString()
        return ResponseEntity.ok(StatementEntryPage(page, next))
    }

    private fun byId(
        space: SpaceId,
        operator: String,
        paymentId: String,
        authorization: String?,
        exchange: OpenFspExchange,
        synchronizing: Boolean,
    ): ResponseEntity<Any> {
        val profile = native(operator) ?: return ResponseEntity.notFound().build()
        val caller = callerOf(operator, authorization)
        if (!caller.authenticated) return exchange.problem(ProblemType.UNAUTHENTICATED)
        if (profile.lookup) {
            return when (val found = operations.lookupPayment(space, operator, caller, paymentId)) {
                is OperatorResponse.Answered -> ResponseEntity.ok(resourceOf(space, operator, found.body))
                is OperatorResponse.Refused -> refused(exchange, found)
            }
        }
        return when (val stored = operations.read(space, operator, caller, paymentId)) {
            is OperatorResponse.Refused -> refused(exchange, stored)
            is OperatorResponse.Answered -> {
                val resource = resourceOf(space, operator, stored.body)
                val terminal = PaymentStatus.fromWire(resource.status)?.isTerminal == true
                if (synchronizing && !terminal) exchange.problem(ProblemType.CAPABILITY_NOT_SUPPORTED, "payments.lookup is not announced") else ResponseEntity.ok(resource)
            }
        }
    }

    private fun created(
        space: SpaceId,
        operator: String,
        scope: IdempotencyScope,
        fingerprint: String,
        exchange: OpenFspExchange,
        create: () -> OperatorResponse<OperatorPayment>,
    ): ResponseEntity<Any> {
        val outcome =
            try {
                create()
            } catch (failure: OpenFspFailure) {
                if (failure.type !in DETERMINED) {
                    idempotency.abandon(scope)
                    throw failure
                }
                return RecordedResponse(failure.type.status, exchange.problem(failure).body!!, null).also { idempotency.finish(scope, fingerprint, it) }.let(::respond)
            } catch (failure: RuntimeException) {
                idempotency.abandon(scope)
                throw failure
            }
        val recorded =
            when (outcome) {
                is OperatorResponse.Answered ->
                    if (outcome.status == CREATED) {
                        RecordedResponse(CREATED, resourceOf(space, operator, outcome.body), outcome.body.paymentId, mapper.location(space, operator, outcome.body.paymentId))
                    } else {
                        RecordedResponse(ProblemType.REFERENCE_CONFLICT.status, exchange.problem(ProblemType.REFERENCE_CONFLICT).body!!, null)
                    }
                is OperatorResponse.Refused -> RecordedResponse(outcome.status, refused(exchange, outcome).body!!, null).takeIf { outcome.status < SERVER_ERROR }
            }
        if (recorded == null) {
            idempotency.abandon(scope)
            return refused(exchange, outcome as OperatorResponse.Refused)
        }
        idempotency.finish(scope, fingerprint, recorded)
        return respond(recorded)
    }

    private fun replay(
        recorded: RecordedResponse,
        exchange: OpenFspExchange,
    ): ResponseEntity<Any> {
        exchange.replaying(recorded.body)
        return ResponseEntity
            .status(recorded.status)
            .header(IDEMPOTENT_REPLAY, "true")
            .apply { recorded.location?.let { location(URI.create(it)) } }
            .apply { if (recorded.status >= BAD_REQUEST) contentType(OpenFspExchange.PROBLEM) }
            .body(recorded.body)
    }

    private fun respond(recorded: RecordedResponse): ResponseEntity<Any> =
        ResponseEntity
            .status(recorded.status)
            .apply { recorded.location?.let { location(URI.create(it)) } }
            .apply { if (recorded.status >= BAD_REQUEST) contentType(OpenFspExchange.PROBLEM) }
            .body(recorded.body)

    private fun stillHeld(
        space: SpaceId,
        operator: String,
        recorded: RecordedResponse,
    ): Boolean = recorded.paymentId == null || operations.checkout(space, operator, recorded.paymentId) != null

    private fun resourceOf(
        space: SpaceId,
        operator: String,
        payment: OperatorPayment,
    ) = mapper.toResource(space, operator, payment, operations.checkout(space, operator, payment.paymentId))

    private fun refused(
        exchange: OpenFspExchange,
        refusal: OperatorResponse.Refused,
    ): ResponseEntity<Any> = exchange.problem(PROBLEMS[refusal.status] ?: ProblemType.INTERNAL_ERROR, refusal.message)

    private fun ResponseEntity<Any>.withRetryAfter(): ResponseEntity<Any> = ResponseEntity.status(statusCode).headers(headers).header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS).body(body)

    private fun native(operator: String): OperatorProfile? = operations.profile(operator)?.takeIf { it.dialect == Dialect.OPENFSP }

    private fun callerOf(
        operator: String,
        authorization: String?,
    ): Caller = Caller(authenticated = principalOf(operator, authorization) != null, credential = authorization.orEmpty())

    private fun principalOf(
        operator: String,
        authorization: String?,
    ): String? {
        val settings = properties.operator(operator)
        return PRINCIPAL_KEYS.firstOrNull { name -> settings.credentialOrNull(name)?.let { caller("Bearer $it", authorization).authenticated } == true }
    }

    companion object {
        const val API_KEY = "api-key"
        const val SECOND_PRINCIPAL_API_KEY = "second-principal-api-key"
        const val IDEMPOTENCY_KEY = "Idempotency-Key"
        const val IDEMPOTENT_REPLAY = "Idempotent-Replay"
        private const val CREATED = 201
        private const val BAD_REQUEST = 400
        private const val NOT_FOUND = 404
        private const val SERVER_ERROR = 500
        private const val RETRY_AFTER_SECONDS = "1"
        private val CACHE: Duration = Duration.ofMinutes(5)
        private val PRINCIPAL_KEYS = listOf(API_KEY, SECOND_PRINCIPAL_API_KEY)
        private const val STATEMENTS_SCOPE = "The key lacks the statements:read scope"
        private val ENTRY_MATCHES = setOf("unmatched", "conflict")
        private val PAGE_SIZES = 1..100
        private const val DEFAULT_PAGE_SIZE = 50
        private val DETERMINED = setOf(ProblemType.MALFORMED_REQUEST, ProblemType.UNKNOWN_FIELD, ProblemType.MISSING_FIELD, ProblemType.INVALID_FIELD)
        private val PROBLEMS =
            mapOf(
                400 to ProblemType.MALFORMED_REQUEST,
                401 to ProblemType.UNAUTHENTICATED,
                404 to ProblemType.NOT_FOUND,
                500 to ProblemType.PROVIDER_ERROR,
            )
    }
}
