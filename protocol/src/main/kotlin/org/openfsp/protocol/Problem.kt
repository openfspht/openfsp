package org.openfsp.protocol

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue

enum class Effect(
    @get:JsonValue val wire: String,
) {
    NONE("none"),
    UNKNOWN("unknown"),
}

enum class ProblemType(
    val code: String,
    val status: Int,
    val retryable: Boolean,
    val effect: Effect,
    val title: String,
) {
    MALFORMED_REQUEST("malformed-request", 400, false, Effect.NONE, "The request body is not a JSON object"),
    UNKNOWN_FIELD("unknown-field", 400, false, Effect.NONE, "The request contains an unknown field"),
    MISSING_FIELD("missing-field", 400, false, Effect.NONE, "A required field is missing"),
    METHOD_NOT_ALLOWED("method-not-allowed", 405, false, Effect.NONE, "Method not allowed on this path"),
    IDEMPOTENCY_KEY_REQUIRED("idempotency-key-required", 400, false, Effect.NONE, "The Idempotency-Key header is required"),
    INVALID_FIELD("invalid-field", 422, false, Effect.NONE, "A field has an invalid value"),
    CAPABILITY_NOT_SUPPORTED("capability-not-supported", 422, false, Effect.NONE, "The capability is not supported"),
    AMOUNT_OUT_OF_RANGE("amount-out-of-range", 422, false, Effect.NONE, "The amount is outside the announced bounds"),
    IDEMPOTENCY_KEY_REUSED("idempotency-key-reused", 422, false, Effect.NONE, "The Idempotency-Key was used with a different body"),
    UNAUTHENTICATED("unauthenticated", 401, false, Effect.NONE, "Authentication is required"),
    FORBIDDEN("forbidden", 403, false, Effect.NONE, "The key is not allowed to perform this operation"),
    NOT_FOUND("not-found", 404, false, Effect.NONE, "Resource not found"),
    REFERENCE_CONFLICT("reference-conflict", 409, false, Effect.NONE, "The reference is already used"),
    IDEMPOTENCY_REQUEST_IN_PROGRESS("idempotency-request-in-progress", 409, true, Effect.UNKNOWN, "A request with this key is in progress"),
    STATE_CONFLICT("state-conflict", 409, false, Effect.NONE, "Operation not allowed from the current state"),
    RATE_LIMITED("rate-limited", 429, true, Effect.NONE, "Too many requests"),
    INTERNAL_ERROR("internal-error", 500, true, Effect.UNKNOWN, "Unexpected gateway failure"),
    PROVIDER_ERROR("provider-error", 502, true, Effect.UNKNOWN, "The provider returned an error or an unusable response"),
    PROVIDER_UNAVAILABLE("provider-unavailable", 503, true, Effect.NONE, "The provider is unreachable"),
    PROVIDER_TIMEOUT("provider-timeout", 504, true, Effect.UNKNOWN, "The provider did not respond in time"),
    ;

    val uri: String get() = "$BASE_URI$code"

    companion object {
        const val BASE_URI = "https://openfsp.org/problems/"
        const val MEDIA_TYPE = "application/problem+json"
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FieldError(
    val field: String,
    val message: String,
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
    @get:JsonProperty("provider_detail") val providerDetail: Map<String, Any?>? = null,
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
