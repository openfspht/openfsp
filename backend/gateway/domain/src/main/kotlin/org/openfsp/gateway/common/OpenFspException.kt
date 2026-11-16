package org.openfsp.gateway.common

import org.openfsp.protocol.Effect
import org.openfsp.protocol.FieldError
import org.openfsp.protocol.FieldErrorCode
import org.openfsp.protocol.JsonPointer
import org.openfsp.protocol.ProblemType
import java.time.Duration

open class OpenFspException(
    val type: ProblemType,
    val detail: String? = null,
    val errors: List<FieldError> = emptyList(),
    val effect: Effect = type.effect,
    val extensions: Map<String, Any?> = emptyMap(),
    val retryAfter: Duration? = null,
) : RuntimeException(detail ?: type.title) {
    companion object {
        fun invalid(
            member: String,
            detail: String,
            code: FieldErrorCode = FieldErrorCode.NOT_ALLOWED,
        ) = OpenFspException(ProblemType.INVALID_FIELD, errors = listOf(FieldError(JsonPointer.of(member), code, detail)))

        fun missing(
            member: String,
            detail: String? = null,
        ) = OpenFspException(ProblemType.MISSING_FIELD, errors = listOf(FieldError(JsonPointer.of(member), FieldErrorCode.REQUIRED, detail)))

        fun notFound() = OpenFspException(ProblemType.NOT_FOUND)
    }
}
