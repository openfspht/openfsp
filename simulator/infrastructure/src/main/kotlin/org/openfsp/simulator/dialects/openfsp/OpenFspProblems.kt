package org.openfsp.simulator.dialects.openfsp

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.protocol.FieldError
import org.openfsp.protocol.Problem
import org.openfsp.protocol.ProblemType
import org.openfsp.protocol.ResourceIds
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

class OpenFspFailure(
    val type: ProblemType,
    val detail: String? = null,
    val errors: List<FieldError>? = null,
) : RuntimeException(detail ?: type.title)

class OpenFspExchange(
    private val request: HttpServletRequest,
    response: HttpServletResponse,
) {
    val requestId: String = ResourceIds.generate("req")

    init {
        response.setHeader(REQUEST_ID, requestId)
    }

    fun problem(failure: OpenFspFailure): ResponseEntity<Any> = problem(failure.type, failure.detail, failure.errors)

    fun problem(
        type: ProblemType,
        detail: String? = null,
        errors: List<FieldError>? = null,
    ): ResponseEntity<Any> {
        val builder = ResponseEntity.status(type.status).contentType(PROBLEM)
        if (type == ProblemType.UNAUTHENTICATED) builder.header(HttpHeaders.WWW_AUTHENTICATE, BEARER)
        return builder.body(Problem.of(type, requestId, request.requestURI, detail, errors))
    }

    companion object {
        const val REQUEST_ID = "Request-Id"
        const val BEARER = "Bearer"
        val PROBLEM: MediaType = MediaType.parseMediaType(ProblemType.MEDIA_TYPE)
    }
}
