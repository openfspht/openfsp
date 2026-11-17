package org.openfsp.gateway.payments.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.gateway.apikeys.RequiresScope
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.idempotency.IdempotencyScope
import org.openfsp.gateway.idempotency.RecordedResponse
import org.openfsp.gateway.idempotency.RequestFingerprints
import org.openfsp.gateway.idempotency.api.Execution
import org.openfsp.gateway.idempotency.api.RunIdempotently
import org.openfsp.gateway.payment.CreationRequest
import org.openfsp.gateway.payment.api.CreateAPayment
import org.openfsp.gateway.payment.api.ReadPayments
import org.openfsp.gateway.payment.api.SynchronizeAPayment
import org.openfsp.gateway.shared.web.DataCollection
import org.openfsp.gateway.shared.web.ProblemResponses
import org.openfsp.gateway.shared.web.RequestIds
import org.openfsp.protocol.IdempotencyKeys
import org.openfsp.protocol.ProblemType
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
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

@RestController
@RequestMapping("/v1/payments")
class PaymentController(
    private val createAPayment: CreateAPayment,
    private val synchronizeAPayment: SynchronizeAPayment,
    private val readPayments: ReadPayments,
    private val requests: CreatePaymentRequestMapper,
    private val resources: PaymentResourceMapper,
    private val idempotency: RunIdempotently,
    private val fingerprints: RequestFingerprints,
    private val problems: ProblemResponses,
    private val json: JsonMapper,
) {
    @RequiresScope(Scope.PAYMENTS_WRITE)
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun create(
        @RequestHeader(IDEMPOTENCY_KEY, required = false) key: String?,
        @RequestBody(required = false) body: ByteArray?,
        request: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): ResponseEntity<ByteArray> {
        key ?: throw OpenFspException(ProblemType.IDEMPOTENCY_KEY_REQUIRED)
        if (!IdempotencyKeys.isValid(key)) throw OpenFspException(ProblemType.INVALID_FIELD, "Idempotency-Key must be 1 to 255 characters among A-Za-z0-9_-")
        val raw = body ?: ByteArray(0)
        val fingerprint =
            try {
                fingerprints.of(raw)
            } catch (_: JacksonException) {
                throw OpenFspException(ProblemType.MALFORMED_REQUEST)
            }
        val scope = IdempotencyScope(request.userPrincipal.name, OPERATION_CREATE, key)
        return when (val execution = idempotency.run(scope, fingerprint, { respond(raw, CreationRequest(scope.principal, key, fingerprint), request) }, ::recorded)) {
            is Execution.Fresh -> execution.value
            is Execution.Replayed -> replay(execution.response, servletResponse)
        }
    }

    private fun respond(
        raw: ByteArray,
        creation: CreationRequest,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> =
        try {
            val command =
                try {
                    requests.toCommand(json.readValue<CreatePaymentRequest>(raw)).copy(request = creation)
                } catch (exception: JacksonException) {
                    throw problems.fromJson(exception)
                }
            val payment = createAPayment.create(command)
            ResponseEntity
                .status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.LOCATION, "/v1/payments/${payment.id}")
                .body(json.writeValueAsBytes(resources.toResource(payment)))
        } catch (exception: OpenFspException) {
            if (exception.type !in DETERMINED) throw exception
            problems.render(exception, request)
        }

    private fun recorded(response: ResponseEntity<ByteArray>) = RecordedResponse(response.statusCode.value(), response.body!!)

    @RequiresScope(Scope.PAYMENTS_READ)
    @GetMapping("/{id}")
    fun read(
        @PathVariable id: String,
    ): PaymentResource = resources.toResource(readPayments.find(id))

    @RequiresScope(Scope.PAYMENTS_READ)
    @GetMapping
    fun findByReference(
        @RequestParam reference: String,
    ): DataCollection<PaymentResource> = DataCollection(listOfNotNull(readPayments.byReference(reference)?.let(resources::toResource)))

    @RequiresScope(Scope.PAYMENTS_WRITE)
    @PostMapping("/{id}/synchronize")
    fun synchronize(
        @PathVariable id: String,
    ): PaymentResource = resources.toResource(synchronizeAPayment.synchronize(id))

    private fun replay(
        recorded: RecordedResponse,
        response: HttpServletResponse,
    ): ResponseEntity<ByteArray> {
        if (recorded.status >= BAD_REQUEST) recordedRequestId(recorded.body)?.let { response.setHeader(RequestIds.HEADER, it) }
        return ResponseEntity
            .status(recorded.status)
            .contentType(if (recorded.status < BAD_REQUEST) MediaType.APPLICATION_JSON else MediaType.parseMediaType(ProblemType.MEDIA_TYPE))
            .header(REPLAY, "true")
            .body(recorded.body)
    }

    private fun recordedRequestId(problem: ByteArray): String? = json.readTree(problem).get(REQUEST_ID)?.takeIf { it.isString }?.asString()

    private companion object {
        const val IDEMPOTENCY_KEY = "Idempotency-Key"
        const val REPLAY = "Idempotent-Replay"
        const val REQUEST_ID = "request_id"
        const val OPERATION_CREATE = "payments.create"
        const val BAD_REQUEST = 400
        val DETERMINED =
            setOf(
                ProblemType.MALFORMED_REQUEST,
                ProblemType.UNKNOWN_FIELD,
                ProblemType.MISSING_FIELD,
                ProblemType.INVALID_FIELD,
                ProblemType.AMOUNT_OUT_OF_RANGE,
                ProblemType.CAPABILITY_NOT_SUPPORTED,
                ProblemType.REFERENCE_CONFLICT,
            )
    }
}
