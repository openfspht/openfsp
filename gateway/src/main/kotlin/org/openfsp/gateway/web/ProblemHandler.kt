package org.openfsp.gateway.web

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.gateway.payment.CallbackTokens
import org.openfsp.protocol.ProblemType
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import tools.jackson.core.JacksonException

@RestControllerAdvice
class ProblemHandler(
    private val responses: ProblemResponses,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun api(
        exception: ApiException,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> = responses.render(exception, request)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(
        exception: HttpMessageNotReadableException,
        request: HttpServletRequest,
    ) = api((exception.cause as? JacksonException)?.let(ProblemResponses::fromJson) ?: ApiException(ProblemType.MALFORMED_REQUEST), request)

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun unsupportedMediaType(request: HttpServletRequest) = api(ApiException(ProblemType.MALFORMED_REQUEST, "Unsupported Content-Type"), request)

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun methodNotAllowed(
        exception: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest,
    ) = api(
        ApiException(ProblemType.METHOD_NOT_ALLOWED, headers = mapOf(HttpHeaders.ALLOW to exception.supportedHttpMethods.orEmpty().joinToString())),
        request,
    )

    @ExceptionHandler(NoResourceFoundException::class)
    fun notFound(request: HttpServletRequest) = api(ApiException(ProblemType.NOT_FOUND), request)

    @ExceptionHandler(Exception::class)
    fun unexpected(
        exception: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> {
        log.error("Unexpected failure on {} {}", request.method, CallbackTokens.masked(request.requestURI), exception)
        return api(ApiException(ProblemType.INTERNAL_ERROR), request)
    }
}
