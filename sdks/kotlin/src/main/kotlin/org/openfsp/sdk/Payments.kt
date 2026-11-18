package org.openfsp.sdk

import org.openfsp.protocol.Effect
import org.openfsp.protocol.IdempotencyKeys
import org.openfsp.protocol.ResourceIds
import org.openfsp.protocol.api.PaymentCollection
import org.openfsp.protocol.api.PaymentResource
import org.openfsp.sdk.http.Api
import org.openfsp.sdk.model.Payment
import org.openfsp.sdk.model.PaymentRequest
import org.openfsp.sdk.protocol.Mapping
import java.util.UUID

class Payments internal constructor(
    private val api: Api,
    private val recoverLostAnswers: Boolean,
) {
    /** Persist the idempotency key before calling: resending with it after a crash cannot charge twice. */
    fun create(
        request: PaymentRequest,
        idempotencyKey: String,
    ): Payment {
        val body = Mapping.toWire(request)
        if (!IdempotencyKeys.isValid(idempotencyKey)) throw InvalidRequestException("idempotencyKey must hold 1 to 255 characters from A-Za-z0-9_-")
        return try {
            Mapping.toPayment(api.post(PAYMENTS, PaymentResource::class.java, body, idempotencyKey))
        } catch (failure: OpenFspException) {
            if (!recoverLostAnswers || !failure.hasUnknownEffect()) throw failure
            recover(request) ?: throw failure
        }
    }

    fun get(id: String): Payment = Mapping.toPayment(api.get("$PAYMENTS/${checked(id)}", PaymentResource::class.java))

    fun synchronize(id: String): Payment = Mapping.toPayment(api.post("$PAYMENTS/${checked(id)}/synchronize", PaymentResource::class.java))

    fun findByReference(reference: String): Payment? {
        if (reference.isBlank()) throw InvalidRequestException("reference must not be empty")
        return api.get(PAYMENTS, PaymentCollection::class.java, mapOf("reference" to reference)).data.firstOrNull()?.let(Mapping::toPayment)
    }

    private fun recover(request: PaymentRequest): Payment? =
        try {
            findByReference(request.reference)?.takeIf { it.matches(request) }
        } catch (lookup: OpenFspException) {
            null
        }

    private fun OpenFspException.hasUnknownEffect(): Boolean = this is TransportException || (this is ApiException && effect == Effect.UNKNOWN)

    private fun checked(id: String): String {
        if (!ResourceIds.isValid(id)) throw InvalidRequestException("payment id must hold 1 to 64 characters from A-Za-z0-9_-")
        return id
    }

    companion object {
        private const val PAYMENTS = "v1/payments"

        @JvmStatic
        fun newIdempotencyKey(): String = UUID.randomUUID().toString()
    }
}
