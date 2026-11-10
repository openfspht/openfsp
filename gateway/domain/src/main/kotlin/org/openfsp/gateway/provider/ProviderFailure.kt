package org.openfsp.gateway.provider

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.protocol.ProblemType

sealed class ProviderFailure(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    class Unavailable(
        cause: Throwable? = null,
    ) : ProviderFailure("The provider is unreachable", cause)

    class Timeout(
        cause: Throwable? = null,
    ) : ProviderFailure("The provider did not respond in time", cause)

    class Rejected(
        message: String,
        val detail: Map<String, Any?>? = null,
    ) : ProviderFailure(message)

    fun toOpenFsp(): OpenFspException =
        when (this) {
            is Unavailable -> OpenFspException(ProblemType.PROVIDER_UNAVAILABLE)
            is Timeout -> OpenFspException(ProblemType.PROVIDER_TIMEOUT)
            is Rejected -> OpenFspException(ProblemType.PROVIDER_ERROR, message, providerDetail = detail)
        }
}
