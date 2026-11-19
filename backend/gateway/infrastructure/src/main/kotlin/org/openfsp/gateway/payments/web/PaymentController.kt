package org.openfsp.gateway.payments.web

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.gateway.apikeys.web.RequiresScope
import org.openfsp.gateway.auth.AuthenticatedKey
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.idempotency.IdempotencyScope
import org.openfsp.gateway.idempotency.RecordedResponse
import org.openfsp.gateway.idempotency.RequestFingerprints
import org.openfsp.gateway.idempotency.api.Execution
import org.openfsp.gateway.idempotency.api.RunIdempotently
import org.openfsp.gateway.payment.CreationRequest
import org.openfsp.gateway.payment.api.CreatePayment
import org.openfsp.gateway.payment.api.ReadPayments
import org.openfsp.gateway.payment.api.SynchronizePayment
import org.openfsp.gateway.shared.web.ProblemResponses
import org.openfsp.protocol.IdempotencyKeys
import org.openfsp.protocol.ProblemType
import org.openfsp.protocol.api.PaymentCollection
import org.openfsp.protocol.api.PaymentResource
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
    private val createPayment: CreatePayment,
    private val synchronizePayment: SynchronizePayment,
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
        caller: AuthenticatedKey,
        request: HttpServletRequest,
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
        val scope = IdempotencyScope(caller.principalId, OPERATION_CREATE, key)
        return when (val execution = idempotency.run(scope, fingerprint, { respond(raw, caller.accountId, CreationRequest(scope.principal, key, fingerprint.value), request) }, ::recorded)) {
            is Execution.Fresh -> execution.value
            is Execution.Replayed -> replay(execution.response)
        }
    }

    private fun respond(
        raw: ByteArray,
        account: String,
        creation: CreationRequest,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> =
        try {
            val command =
                try {
                    requests.toCommand(account, json.readValue<CreatePaymentRequest>(raw)).copy(request = creation)
                } catch (exception: JacksonException) {
                    throw problems.fromJson(exception)
                }
            val payment = createPayment.create(command)
            ResponseEntity
                .status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.LOCATION, location(payment.id))
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
        caller: AuthenticatedKey,
    ): PaymentResource = resources.toResource(synchronizePayment.current(caller.accountId, id))

    @RequiresScope(Scope.PAYMENTS_READ)
    @GetMapping
    fun findByReference(
        @RequestParam reference: String,
        caller: AuthenticatedKey,
    ): PaymentCollection = resources.toCollection(listOfNotNull(readPayments.byReference(caller.accountId, reference)?.let { synchronizePayment.current(caller.accountId, it.id) }))

    private fun replay(recorded: RecordedResponse): ResponseEntity<ByteArray> {
        val response =
            ResponseEntity
                .status(recorded.status)
                .contentType(if (recorded.status < BAD_REQUEST) MediaType.APPLICATION_JSON else MediaType.parseMediaType(ProblemType.MEDIA_TYPE))
                .header(REPLAY, "true")
        if (recorded.status == HttpStatus.CREATED.value()) recordedPaymentId(recorded.body)?.let { response.header(HttpHeaders.LOCATION, location(it)) }
        return response.body(recorded.body)
    }

    private fun recordedPaymentId(payment: ByteArray): String? = json.readTree(payment).get(ID)?.takeIf { it.isString }?.asString()

    private fun location(paymentId: String) = "/v1/payments/$paymentId"

    private companion object {
        const val IDEMPOTENCY_KEY = "Idempotency-Key"
        const val REPLAY = "Idempotent-Replay"
        const val ID = "id"
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
