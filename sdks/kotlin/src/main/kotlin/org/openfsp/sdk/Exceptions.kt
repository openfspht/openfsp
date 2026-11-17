package org.openfsp.sdk

import org.openfsp.protocol.Effect
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
) : OpenFspException("$status $code${requestId?.let { " (request $it)" }.orEmpty()}")

class PaymentNotFoundException internal constructor(
    status: Int,
    code: String,
    title: String,
    detail: String?,
    requestId: String?,
) : ApiException(status, code, title, detail, false, Effect.NONE, requestId, emptyList(), null)
