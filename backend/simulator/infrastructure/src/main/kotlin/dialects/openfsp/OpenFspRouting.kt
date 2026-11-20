package org.openfsp.simulator.dialects.openfsp

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.protocol.ProblemType
import org.openfsp.simulator.dialects.DialectGuard
import org.openfsp.simulator.operator.Dialect
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class OpenFspRouting(
    private val guard: DialectGuard,
) {
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun methodNotAllowed(
        exception: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> {
        if (!isOpenFspApi(request)) throw exception
        val problem = OpenFspExchange(request, response).problem(ProblemType.METHOD_NOT_ALLOWED)
        val allowed = exception.supportedHttpMethods.orEmpty().joinToString { it.name() }
        return ResponseEntity.status(problem.statusCode).headers(problem.headers).header(HttpHeaders.ALLOW, allowed).body(problem.body)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(
        exception: HttpMessageNotReadableException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> {
        if (!isOpenFspApi(request)) throw exception
        return OpenFspExchange(request, response).problem(ProblemType.MALFORMED_REQUEST)
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun notFound(
        exception: NoResourceFoundException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> {
        if (!isOpenFspApi(request)) throw exception
        return OpenFspExchange(request, response).problem(ProblemType.NOT_FOUND)
    }

    private fun isOpenFspApi(request: HttpServletRequest): Boolean {
        val segments = request.requestURI.removePrefix(request.contextPath).removePrefix("/").split('/')
        if (segments.size < 2 || segments[1] !in API_ROOTS) return false
        return guard.speaks(segments[0], Dialect.OPENFSP)
    }

    private companion object {
        val API_ROOTS = setOf("v1", ".well-known", "conformance")
    }
}
