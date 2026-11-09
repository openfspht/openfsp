package org.openfsp.gateway.web

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.gateway.payment.CallbackTokens
import org.openfsp.protocol.FieldError
import org.openfsp.protocol.Problem
import org.openfsp.protocol.ProblemType
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
        exception: ApiException,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> {
        val problem =
            Problem
                .of(exception.type, RequestIds.of(request), CallbackTokens.masked(request.requestURI), exception.detail, exception.errors, exception.effect)
                .copy(providerDetail = exception.providerDetail, extensions = exception.extensions)
        return ResponseEntity
            .status(HttpStatus.valueOf(problem.status))
            .contentType(MediaType.parseMediaType(ProblemType.MEDIA_TYPE))
            .headers { http -> exception.headers.forEach(http::set) }
            .body(json.writeValueAsBytes(problem))
    }

    companion object {
        fun fromJson(exception: JacksonException): ApiException =
            when (exception) {
                is UnrecognizedPropertyException ->
                    ApiException(
                        ProblemType.UNKNOWN_FIELD,
                        "Unknown field: ${exception.path()}",
                        listOf(FieldError(exception.path(), "unknown field")),
                    )
                is MismatchedInputException ->
                    if (exception.path.isEmpty()) {
                        ApiException(ProblemType.MALFORMED_REQUEST)
                    } else {
                        ApiException(ProblemType.INVALID_FIELD, errors = listOf(FieldError(exception.path(), "has an invalid type or value")))
                    }
                else -> ApiException(ProblemType.MALFORMED_REQUEST)
            }

        private fun DatabindException.path(): String = path.joinToString(".") { it.propertyName ?: it.index.toString() }
    }
}
