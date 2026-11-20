package org.openfsp.server

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import org.openfsp.protocol.Effect
import org.openfsp.protocol.ProblemType

enum class FieldErrorCode(
    @get:JsonValue val wire: String,
) {
    REQUIRED("required"),
    MALFORMED("malformed"),
    INVALID_TYPE("invalid_type"),
    UNKNOWN_FIELD("unknown_field"),
    NOT_POSITIVE("not_positive"),
    TOO_LARGE("too_large"),
    TOO_LONG("too_long"),
    TOO_MANY("too_many"),
    NOT_ALLOWED("not_allowed"),
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FieldError(
    val field: String,
    val code: FieldErrorCode,
    val detail: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Problem(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String?,
    val instance: String?,
    val retryable: Boolean,
    val effect: Effect,
    @get:JsonProperty("request_id") val requestId: String,
    val errors: List<FieldError>? = null,
    @get:JsonAnyGetter val extensions: Map<String, Any?> = emptyMap(),
) {
    companion object {
        fun of(
            type: ProblemType,
            requestId: String,
            instance: String? = null,
            detail: String? = null,
            errors: List<FieldError>? = null,
            effect: Effect = type.effect,
        ) = Problem(
            type = type.uri,
            title = type.title,
            status = type.status,
            detail = detail,
            instance = instance,
            retryable = type.retryable,
            effect = effect,
            requestId = requestId,
            errors = errors?.takeIf { it.isNotEmpty() },
        )
    }
}
