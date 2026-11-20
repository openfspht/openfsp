package org.openfsp.gateway.provider

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.protocol.Effect
import org.openfsp.protocol.ProblemType
import java.time.Duration

sealed class ProviderFailure(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    class Unavailable(
        cause: Throwable? = null,
        val retryAfter: Duration? = null,
        message: String = "The provider is unreachable",
    ) : ProviderFailure(message, cause)

    class Timeout(
        cause: Throwable? = null,
    ) : ProviderFailure("The provider did not respond in time", cause)

    class InProgress(
        val retryAfter: Duration? = null,
    ) : ProviderFailure("The provider is still processing the same creation")

    class Rejected(
        message: String,
        val detail: Map<String, Any?>? = null,
        val withoutEffect: Boolean = false,
    ) : ProviderFailure(message) {
        val problemDetail: String
            get() = listOfNotNull(detail?.get(CODE), detail?.get(MESSAGE)).joinToString(": ").ifEmpty { message.orEmpty() }

        private companion object {
            const val CODE = "code"
            const val MESSAGE = "message"
        }
    }

    private companion object {
        val DEFAULT_RETRY_AFTER: Duration = Duration.ofSeconds(1)
    }

    fun toOpenFsp(): OpenFspException =
        when (this) {
            is Unavailable -> OpenFspException(ProblemType.PROVIDER_UNAVAILABLE, retryAfter = retryAfter)
            is Timeout -> OpenFspException(ProblemType.PROVIDER_TIMEOUT)
            is InProgress -> OpenFspException(ProblemType.IDEMPOTENCY_REQUEST_IN_PROGRESS, retryAfter = retryAfter ?: DEFAULT_RETRY_AFTER)
            is Rejected -> OpenFspException(ProblemType.PROVIDER_ERROR, problemDetail, effect = if (withoutEffect) Effect.NONE else Effect.UNKNOWN)
        }
}
