package org.openfsp.gateway.payment

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.gateway.auth.RequiresScope
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.idempotency.Claim
import org.openfsp.gateway.idempotency.Idempotency
import org.openfsp.gateway.idempotency.IdempotencyScope
import org.openfsp.gateway.web.ApiException
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

@RestController
@RequestMapping("/v1/payments")
class PaymentController(
    private val payments: Payments,
    private val idempotency: Idempotency,
    private val problems: ProblemResponses,
    private val properties: org.openfsp.gateway.GatewayProperties,
    private val json: JsonMapper,
) {
    @RequiresScope(Scope.PAYMENTS_WRITE)
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun create(
        @RequestHeader(IDEMPOTENCY_KEY, required = false) key: String?,
        @RequestBody(required = false) body: ByteArray?,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> {
        key ?: throw ApiException(ProblemType.IDEMPOTENCY_KEY_REQUIRED)
        if (!IdempotencyKeys.isValid(key)) throw ApiException(ProblemType.INVALID_FIELD, "Idempotency-Key must be 1 to 255 characters among A-Za-z0-9_-")
        val raw = body ?: ByteArray(0)
        val fingerprint =
            try {
                idempotency.fingerprint(raw)
            } catch (_: JacksonException) {
                throw ApiException(ProblemType.MALFORMED_REQUEST)
            }

        val scope = IdempotencyScope(principal(request), OPERATION_CREATE, key)
        when (val claim = idempotency.claim(scope, fingerprint)) {
            is Claim.Replay ->
                return ResponseEntity
                    .status(claim.status)
                    .contentType(if (claim.status < 400) MediaType.APPLICATION_JSON else MediaType.parseMediaType(ProblemType.MEDIA_TYPE))
                    .header(REPLAY, "true")
                    .body(claim.body)
            Claim.InProgress -> throw ApiException(ProblemType.IDEMPOTENCY_REQUEST_IN_PROGRESS, headers = mapOf(HttpHeaders.RETRY_AFTER to Idempotency.RETRY_AFTER_SECONDS))
            Claim.KeyReused -> throw ApiException(ProblemType.IDEMPOTENCY_KEY_REUSED)
            Claim.Acquired -> Unit
        }

        val response =
            try {
                val command =
                    try {
                        json.readValue<CreatePaymentRequest>(raw).validate(properties.developmentMode)
                    } catch (exception: JacksonException) {
                        throw ProblemResponses.fromJson(exception)
                    }
                val payment = payments.create(command)
                ResponseEntity
                    .status(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.LOCATION, "/v1/payments/${payment.id}")
                    .body(json.writeValueAsBytes(PaymentResource.of(payment, json)))
            } catch (exception: ApiException) {
                if (exception.type !in DETERMINED_ERRORS) {
                    idempotency.release(scope)
                    throw exception
                }
                problems.render(exception, request)
            } catch (exception: Exception) {
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
    ): PaymentResource = PaymentResource.of(payments.find(id), json)

    @RequiresScope(Scope.PAYMENTS_READ)
    @GetMapping
    fun findByReference(
        @RequestParam reference: String,
    ): PaymentCollection = PaymentCollection(listOfNotNull(payments.findByReference(reference)?.let { PaymentResource.of(it, json) }))

    @RequiresScope(Scope.PAYMENTS_WRITE)
    @PostMapping("/{id}/synchronize")
    fun synchronize(
        @PathVariable id: String,
    ): PaymentResource = PaymentResource.of(payments.synchronize(id), json)

    private fun principal(request: HttpServletRequest): String = request.userPrincipal.name

    private companion object {
        const val IDEMPOTENCY_KEY = "Idempotency-Key"
        const val REPLAY = "Idempotent-Replay"
        const val OPERATION_CREATE = "payments.create"
        val DETERMINED_ERRORS =
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
