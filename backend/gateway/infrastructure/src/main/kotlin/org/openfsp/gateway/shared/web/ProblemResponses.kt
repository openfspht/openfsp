package org.openfsp.gateway.shared.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.protocol.FieldError
import org.openfsp.protocol.FieldErrorCode
import org.openfsp.protocol.JsonPointer
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
    private val paths: RequestPaths,
) {
    fun render(
        exception: OpenFspException,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> {
        val problem =
            Problem
                .of(exception.type, RequestIds.of(request), paths.safe(request.requestURI), exception.detail, exception.errors, exception.effect)
                .copy(extensions = exception.extensions)
        val headers =
            listOfNotNull(exception.retryAfter?.let { HttpHeaders.RETRY_AFTER to it.seconds.coerceAtLeast(1).toString() }).toMap() +
                (exception as? HttpProblem)?.headers.orEmpty()
        return render(problem, headers)
    }

    fun render(
        problem: Problem,
        headers: Map<String, String> = emptyMap(),
    ): ResponseEntity<ByteArray> =
        ResponseEntity
            .status(HttpStatus.valueOf(problem.status))
            .contentType(MediaType.parseMediaType(ProblemType.MEDIA_TYPE))
            .headers { http -> headers.forEach(http::set) }
            .body(json.writeValueAsBytes(problem))

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
                OpenFspException(ProblemType.UNKNOWN_FIELD, "Unknown field: ${exception.pointer()}", listOf(FieldError(exception.pointer(), FieldErrorCode.UNKNOWN_FIELD)))
            is MismatchedInputException ->
                if (exception.path.isEmpty()) {
                    OpenFspException(ProblemType.MALFORMED_REQUEST)
                } else {
                    OpenFspException(ProblemType.INVALID_FIELD, errors = listOf(FieldError(exception.pointer(), FieldErrorCode.INVALID_TYPE, "has an invalid type or value")))
                }
            else -> OpenFspException(ProblemType.MALFORMED_REQUEST)
        }

    private fun DatabindException.pointer(): String = JsonPointer.of(*path.map { it.propertyName ?: it.index.toString() }.toTypedArray())
}
