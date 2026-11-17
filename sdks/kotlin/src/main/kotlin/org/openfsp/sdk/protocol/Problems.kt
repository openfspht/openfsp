package org.openfsp.sdk.protocol

import org.openfsp.protocol.Effect
import org.openfsp.protocol.ProblemType
import org.openfsp.sdk.ApiException
import org.openfsp.sdk.FieldError
import org.openfsp.sdk.PaymentNotFoundException
import org.openfsp.sdk.http.TransportResponse
import tools.jackson.databind.JsonNode
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

internal object Problems {
    private const val UNEXPECTED = "unexpected-response"
    private val GATEWAY_ERRORS = setOf(502, 503, 504)

    fun from(
        response: TransportResponse,
        now: Instant,
    ): ApiException {
        val requestId = response.header("Request-Id")
        val retryAfter = response.header("Retry-After")?.let { parseRetryAfter(it, now) }
        val problem = Json.tree(response.body)
        val type =
            problem?.text("type")
                ?: return unexpected(response.status, "The server did not answer with a problem document", requestId, retryAfter, response.status in GATEWAY_ERRORS)
        val code = type.removePrefix(ProblemType.BASE_URI)
        val title = problem.text("title") ?: code
        val detail = problem.text("detail")
        val id = problem.text("request_id") ?: requestId
        if (code == ProblemType.NOT_FOUND.code) return PaymentNotFoundException(response.status, code, title, detail, id)
        return ApiException(
            status = response.status,
            code = code,
            title = title,
            detail = detail,
            retryable = problem.get("retryable")?.takeIf { it.isBoolean }?.booleanValue() ?: false,
            effect = if (problem.text("effect") == Effect.NONE.wire) Effect.NONE else Effect.UNKNOWN,
            requestId = id,
            fieldErrors = problem.get("errors")?.takeIf { it.isArray }?.mapNotNull(::fieldError).orEmpty(),
            retryAfter = retryAfter,
        )
    }

    fun unexpected(
        status: Int,
        title: String,
        requestId: String? = null,
        retryAfter: Duration? = null,
        retryable: Boolean = false,
    ) = ApiException(status, UNEXPECTED, title, null, retryable, Effect.UNKNOWN, requestId, emptyList(), retryAfter)

    private fun fieldError(node: JsonNode): FieldError? {
        val field = node.text("field") ?: return null
        val code = node.text("code") ?: return null
        return FieldError(field, code, node.text("detail"))
    }

    private fun parseRetryAfter(
        value: String,
        now: Instant,
    ): Duration? {
        val trimmed = value.trim()
        trimmed.toLongOrNull()?.let { return Duration.ofSeconds(it.coerceAtLeast(0)) }
        return try {
            Duration.between(now, ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).takeUnless { it.isNegative } ?: Duration.ZERO
        } catch (unparsable: DateTimeParseException) {
            null
        }
    }

    private fun JsonNode.text(name: String): String? = get(name)?.takeIf { it.isString }?.stringValue()
}
