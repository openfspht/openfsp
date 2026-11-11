package org.openfsp.validator.web.problems

import org.springframework.http.HttpStatus
import java.time.Duration

data class FieldProblem(
    val pointer: String,
    val detail: String,
)

enum class ProblemKind(
    val slug: String,
    val status: HttpStatus,
    val title: String,
) {
    INVALID_REQUEST("invalid-request", HttpStatus.BAD_REQUEST, "The run request is invalid"),
    TEST_KEY_REQUIRED("test-key-required", HttpStatus.UNPROCESSABLE_CONTENT, "Only test keys are accepted"),
    PRIVATE_ADDRESS("private-address", HttpStatus.UNPROCESSABLE_CONTENT, "The base URL does not resolve to a public address"),
    UNRESOLVABLE_HOST("unresolvable-host", HttpStatus.UNPROCESSABLE_CONTENT, "The base URL host does not resolve"),
    BUSY("busy", HttpStatus.CONFLICT, "Every run slot is in use"),
    RUN_IN_PROGRESS("run-in-progress", HttpStatus.CONFLICT, "The run is not finished"),
    RUN_NOT_STARTED("run-not-started", HttpStatus.CONFLICT, "The run is prepared but not started"),
    RUN_STARTED("run-started", HttpStatus.CONFLICT, "The run has already started"),
    SIMULATOR_UNAVAILABLE("simulator-unavailable", HttpStatus.SERVICE_UNAVAILABLE, "The simulator is unavailable"),
    RATE_LIMITED("rate-limited", HttpStatus.TOO_MANY_REQUESTS, "Too many runs from this address"),
    NOT_FOUND("not-found", HttpStatus.NOT_FOUND, "Not found"),
    VERSION_REFUSED("version-refused", HttpStatus.UNPROCESSABLE_CONTENT, "The target announces another major protocol version"),
    RUN_FAILED("run-failed", HttpStatus.INTERNAL_SERVER_ERROR, "The run stopped before its end"),
    METHOD_NOT_ALLOWED("method-not-allowed", HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    UNSUPPORTED_MEDIA_TYPE("unsupported-media-type", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
    INTERNAL("internal", HttpStatus.INTERNAL_SERVER_ERROR, "Internal error"),
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
        const val MEDIA_TYPE = "application/problem+json"

        fun of(
            kind: ProblemKind,
            detail: String?,
            instance: String?,
            errors: List<FieldProblem> = emptyList(),
        ) = ProblemBody(kind.type, kind.title, kind.status.value(), detail, instance, errors.ifEmpty { null })
    }
}
