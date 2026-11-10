package org.openfsp.gateway.common

import org.openfsp.protocol.Effect
import org.openfsp.protocol.FieldError
import org.openfsp.protocol.ProblemType
import java.time.Duration

open class OpenFspException(
    val type: ProblemType,
    val detail: String? = null,
    val errors: List<FieldError> = emptyList(),
    val effect: Effect = type.effect,
    val providerDetail: Map<String, Any?>? = null,
    val extensions: Map<String, Any?> = emptyMap(),
    val retryAfter: Duration? = null,
) : RuntimeException(detail ?: type.title) {
    companion object {
        fun invalid(
            field: String,
            message: String,
        ) = OpenFspException(ProblemType.INVALID_FIELD, errors = listOf(FieldError(field, message)))

        fun missing(
            field: String,
            message: String = "is required",
        ) = OpenFspException(ProblemType.MISSING_FIELD, errors = listOf(FieldError(field, message)))

        fun notFound() = OpenFspException(ProblemType.NOT_FOUND)
    }
}
