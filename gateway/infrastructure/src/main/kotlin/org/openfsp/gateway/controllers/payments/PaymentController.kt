package org.openfsp.gateway.controllers.payments

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.idempotency.Claim
import org.openfsp.gateway.idempotency.Idempotency
import org.openfsp.gateway.idempotency.IdempotencyRecordId
import org.openfsp.gateway.idempotency.RequestFingerprints
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.api.CreateAPayment
import org.openfsp.gateway.payment.api.SynchronizeAPayment
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.security.RateLimits
import org.openfsp.gateway.security.RequiresScope
import org.openfsp.gateway.web.ProblemResponses
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
import java.time.Duration

@RestController
@RequestMapping("/v1/payments")
class PaymentController(
    private val createAPayment: CreateAPayment,
    private val synchronizeAPayment: SynchronizeAPayment,
    private val payments: Payments,
    private val requests: CreatePaymentRequestMapper,
    private val resources: PaymentResourceMapper,
    private val idempotency: Idempotency,
    private val fingerprints: RequestFingerprints,
    private val problems: ProblemResponses,
    private val rateLimits: RateLimits,
    private val json: JsonMapper,
) {
    @RequiresScope(Scope.PAYMENTS_WRITE)
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun create(
        @RequestHeader(IDEMPOTENCY_KEY, required = false) key: String?,
        @RequestBody(required = false) body: ByteArray?,
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
        val scope = IdempotencyRecordId(request.userPrincipal.name, OPERATION_CREATE, key)
        when (val claim = idempotency.claim(scope, fingerprint)) {
            is Claim.Replay -> return replay(claim)
            Claim.InProgress ->
                throw OpenFspException(ProblemType.IDEMPOTENCY_REQUEST_IN_PROGRESS, retryAfter = Duration.ofSeconds(Idempotency.RETRY_AFTER_SECONDS))
            Claim.KeyReused -> throw OpenFspException(ProblemType.IDEMPOTENCY_KEY_REUSED)
            Claim.Acquired -> Unit
        }
        val response =
            try {
                val command =
                    try {
                        requests.toCommand(json.readValue<CreatePaymentRequest>(raw))
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
                if (exception.type !in DETERMINED) {
                    idempotency.release(scope)
                    throw exception
                }
                problems.render(exception, request)
            } catch (exception: RuntimeException) {
                idempotency.release(scope)
                throw exception
            }
        idempotency.complete(scope, response.statusCode.value(), response.body!!)
        return response
    }

    @RequiresScope(Scope.PAYMENTS_READ)
    @GetMapping("/{id}")
    fun read(
        @PathVariable id: String,
    ): PaymentResource = resources.toResource(visible(payments.find(id)))

    @RequiresScope(Scope.PAYMENTS_READ)
    @GetMapping
    fun findByReference(
        @RequestParam reference: String,
    ): Collection<PaymentResource> = Collection(listOfNotNull(payments.withReference(reference)?.takeIf(Payment::isVisible)?.let(resources::toResource)))

    @RequiresScope(Scope.PAYMENTS_WRITE)
    @PostMapping("/{id}/synchronize")
    fun synchronize(
        @PathVariable id: String,
    ): PaymentResource {
        visible(payments.find(id)).takeIf { it.isTerminal }?.let { return resources.toResource(it) }
        rateLimits.synchronization(id)
        return resources.toResource(visible(synchronizeAPayment.synchronize(id)))
    }

    private fun visible(payment: Payment?): Payment = payment?.takeIf(Payment::isVisible) ?: throw OpenFspException.notFound()

    private fun replay(claim: Claim.Replay): ResponseEntity<ByteArray> =
        ResponseEntity
            .status(claim.status)
            .contentType(if (claim.status < BAD_REQUEST) MediaType.APPLICATION_JSON else MediaType.parseMediaType(ProblemType.MEDIA_TYPE))
            .header(REPLAY, "true")
            .body(claim.body)

    private companion object {
        const val IDEMPOTENCY_KEY = "Idempotency-Key"
        const val REPLAY = "Idempotent-Replay"
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
