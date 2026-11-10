package org.openfsp.gateway.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.protocol.FieldError
import org.openfsp.protocol.Problem
import org.openfsp.protocol.ProblemType
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.DatabindException
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.databind.exc.UnrecognizedPropertyException
import tools.jackson.databind.json.JsonMapper

@Component
class ProblemResponses(
    private val json: JsonMapper,
) {
    fun render(
        exception: OpenFspException,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> {
        val problem =
            Problem
                .of(exception.type, RequestIds.of(request), RequestPaths.safe(request.requestURI), exception.detail, exception.errors, exception.effect)
                .copy(providerDetail = exception.providerDetail, extensions = exception.extensions)
        return ResponseEntity
            .status(HttpStatus.valueOf(problem.status))
            .contentType(MediaType.parseMediaType(ProblemType.MEDIA_TYPE))
            .headers { http ->
                exception.retryAfter?.let { http.set(HttpHeaders.RETRY_AFTER, it.seconds.coerceAtLeast(1).toString()) }
                (exception as? HttpProblem)?.headers?.forEach(http::set)
            }.body(json.writeValueAsBytes(problem))
    }

    fun write(
        exception: OpenFspException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val rendered = render(exception, request)
        response.status = rendered.statusCode.value()
        rendered.headers.forEach { name, values -> values.forEach { response.addHeader(name, it) } }
        response.outputStream.write(rendered.body!!)
    }

    fun fromJson(exception: JacksonException): OpenFspException =
        when (exception) {
            is UnrecognizedPropertyException ->
                OpenFspException(ProblemType.UNKNOWN_FIELD, "Unknown field: ${exception.path()}", listOf(FieldError(exception.path(), "unknown field")))
            is MismatchedInputException ->
                if (exception.path.isEmpty()) {
                    OpenFspException(ProblemType.MALFORMED_REQUEST)
                } else {
                    OpenFspException.invalid(exception.path(), "has an invalid type or value")
                }
            else -> OpenFspException(ProblemType.MALFORMED_REQUEST)
        }

    private fun DatabindException.path(): String = path.joinToString(".") { it.propertyName ?: it.index.toString() }
}
