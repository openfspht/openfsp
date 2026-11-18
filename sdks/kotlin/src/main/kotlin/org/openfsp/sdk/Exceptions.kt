package org.openfsp.sdk

import org.openfsp.protocol.Effect
import org.openfsp.sdk.model.Money
import java.time.Duration

sealed class OpenFspException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class ConfigurationException(
    message: String,
) : OpenFspException(message)

class InvalidRequestException(
    message: String,
) : OpenFspException(message)

class InvalidSignatureException(
    message: String,
) : OpenFspException(message)

class UnknownEventTypeException(
    val eventId: String,
    val eventType: String,
) : OpenFspException("unknown event type $eventType")

class WebhookKeysUnavailableException(
    message: String,
    cause: Throwable? = null,
) : OpenFspException(message, cause)

class TransportException(
    message: String,
    cause: Throwable? = null,
) : OpenFspException(message, cause) {
    val effect: Effect get() = Effect.UNKNOWN
}

data class FieldError(
    val field: String,
    val code: String,
    val detail: String? = null,
)

open class ApiException internal constructor(
    val status: Int,
    val code: String,
    val title: String,
    val detail: String?,
    val retryable: Boolean,
    val effect: Effect,
    val requestId: String?,
    val fieldErrors: List<FieldError>,
    val retryAfter: Duration?,
    val instance: String? = null,
    val minAmount: Money? = null,
    val maxAmount: Money? = null,
    val amountStep: Money? = null,
) : OpenFspException("$status $code${requestId?.let { " (request $it)" }.orEmpty()}")

class PaymentNotFoundException internal constructor(
    status: Int,
    code: String,
    title: String,
    detail: String?,
    requestId: String?,
) : ApiException(status, code, title, detail, false, Effect.NONE, requestId, emptyList(), null)

class UnknownPaymentStatusException internal constructor(
    val paymentId: String,
    val reference: String,
    val unknownStatus: String,
) : ApiException(200, "unexpected-response", "unknown payment status $unknownStatus", null, false, Effect.UNKNOWN, null, emptyList(), null)
