package org.openfsp.gateway.shared.web

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.protocol.Effect
import org.openfsp.protocol.Problem
import org.openfsp.protocol.ProblemType
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import tools.jackson.core.JacksonException

@RestControllerAdvice
class ProblemHandler(
    private val responses: ProblemResponses,
    properties: GatewayProperties,
) {
    private val notAcceptableType = properties.publicBaseUrl.resolve("/problems/not-acceptable").toString()

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(OpenFspException::class)
    fun openFsp(
        exception: OpenFspException,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> = responses.render(exception, request)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(
        exception: HttpMessageNotReadableException,
        request: HttpServletRequest,
    ) = openFsp((exception.cause as? JacksonException)?.let(responses::fromJson) ?: OpenFspException(ProblemType.MALFORMED_REQUEST), request)

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun missingParameter(
        exception: MissingServletRequestParameterException,
        request: HttpServletRequest,
    ) = openFsp(OpenFspException.missing(exception.parameterName), request)

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun unsupportedMediaType(request: HttpServletRequest) = openFsp(OpenFspException(ProblemType.MALFORMED_REQUEST, "Unsupported Content-Type"), request)

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun methodNotAllowed(
        exception: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest,
    ) = openFsp(
        HttpProblem(ProblemType.METHOD_NOT_ALLOWED, headers = mapOf(HttpHeaders.ALLOW to exception.supportedHttpMethods.orEmpty().joinToString())),
        request,
    )

    @ExceptionHandler(HttpMediaTypeNotAcceptableException::class)
    fun notAcceptable(request: HttpServletRequest): ResponseEntity<ByteArray> =
        responses.render(
            Problem(
                type = notAcceptableType,
                title = "No acceptable representation",
                status = HttpStatus.NOT_ACCEPTABLE.value(),
                detail = "The response is available as ${MediaType.APPLICATION_JSON_VALUE} or ${ProblemType.MEDIA_TYPE}",
                instance = RequestPaths.safe(request.requestURI),
                retryable = false,
                effect = Effect.NONE,
                requestId = RequestIds.of(request),
            ),
        )

    @ExceptionHandler(NoResourceFoundException::class)
    fun notFound(request: HttpServletRequest) = openFsp(OpenFspException.notFound(), request)

    @ExceptionHandler(Exception::class)
    fun unexpected(
        exception: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> {
        log.error("Unexpected failure on {} {}", request.method, RequestPaths.safe(request.requestURI), exception)
        return openFsp(OpenFspException(ProblemType.INTERNAL_ERROR), request)
    }
}
