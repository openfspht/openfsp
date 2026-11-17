package org.openfsp.validator.problem

import java.time.Duration

data class FieldProblem(
    val pointer: String,
    val detail: String,
)

enum class ProblemKind(
    val slug: String,
    val status: Int,
    val title: String,
) {
    INVALID_REQUEST("invalid-request", 400, "The run request is invalid"),
    TEST_KEY_REQUIRED("test-key-required", 422, "Only test keys are accepted"),
    CREDENTIALS_REQUIRED("credentials-required", 422, "The server does not prepare conformance runs"),
    ACCESS_CODE_REQUIRED("access-code-required", 422, "The server asks for a conformance access code"),
    PREPARATION_FAILED("preparation-failed", 422, "The server could not prepare the run"),
    PRIVATE_ADDRESS("private-address", 422, "The base URL does not resolve to a public address"),
    UNRESOLVABLE_HOST("unresolvable-host", 422, "The base URL host does not resolve"),
    BUSY("busy", 409, "Every run slot is in use"),
    RUN_IN_PROGRESS("run-in-progress", 409, "The run is not finished"),
    RUN_NOT_STARTED("run-not-started", 409, "The run is prepared but not started"),
    RUN_STARTED("run-started", 409, "The run has already started"),
    ATTESTATION_UNAVAILABLE("attestation-unavailable", 503, "Attestations cannot be issued"),
    SIMULATOR_UNAVAILABLE("simulator-unavailable", 503, "The simulator is unavailable"),
    RATE_LIMITED("rate-limited", 429, "Too many runs from this address"),
    NOT_FOUND("not-found", 404, "Not found"),
    VERSION_REFUSED("version-refused", 422, "The target announces another major protocol version"),
    RUN_FAILED("run-failed", 500, "The run stopped before its end"),
    METHOD_NOT_ALLOWED("method-not-allowed", 405, "Method not allowed"),
    UNSUPPORTED_MEDIA_TYPE("unsupported-media-type", 415, "Unsupported media type"),
    INTERNAL("internal", 500, "Internal error"),
    ;

    val type: String get() = "$TYPE_BASE$slug"

    companion object {
        const val TYPE_BASE = "https://openfsp.org/problems/validator/"
    }
}

class ValidatorException(
    val kind: ProblemKind,
    val detail: String? = null,
    val errors: List<FieldProblem> = emptyList(),
    val retryAfter: Duration? = null,
) : RuntimeException(detail ?: kind.title)

data class ProblemBody(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String?,
    val instance: String?,
    val errors: List<FieldProblem>?,
) {
    companion object {
        fun of(
            kind: ProblemKind,
            detail: String?,
            instance: String?,
            errors: List<FieldProblem> = emptyList(),
        ) = ProblemBody(kind.type, kind.title, kind.status, detail, instance, errors.ifEmpty { null })
    }
}
