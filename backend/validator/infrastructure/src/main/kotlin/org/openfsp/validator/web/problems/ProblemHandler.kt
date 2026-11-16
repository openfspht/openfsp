package org.openfsp.validator.web.problems

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.validator.problem.FieldProblem
import org.openfsp.validator.problem.ProblemBody
import org.openfsp.validator.problem.ProblemKind
import org.openfsp.validator.problem.ValidatorException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import tools.jackson.databind.json.JsonMapper

@RestControllerAdvice
class ProblemHandler(
    private val json: JsonMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ValidatorException::class)
    fun validator(
        exception: ValidatorException,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> = render(exception.kind, exception.detail, request, exception.errors, exception.retryAfter?.seconds?.coerceAtLeast(1))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(request: HttpServletRequest): ResponseEntity<ByteArray> = render(ProblemKind.INVALID_REQUEST, "The body is not a valid run request.", request)

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun mismatch(request: HttpServletRequest): ResponseEntity<ByteArray> = render(ProblemKind.NOT_FOUND, null, request)

    @ExceptionHandler(NoResourceFoundException::class)
    fun missing(request: HttpServletRequest): ResponseEntity<ByteArray> = render(ProblemKind.NOT_FOUND, null, request)

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun method(request: HttpServletRequest): ResponseEntity<ByteArray> = render(ProblemKind.METHOD_NOT_ALLOWED, null, request)

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun mediaType(request: HttpServletRequest): ResponseEntity<ByteArray> = render(ProblemKind.UNSUPPORTED_MEDIA_TYPE, "Send application/json.", request)

    @ExceptionHandler(Exception::class)
    fun unexpected(
        exception: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> {
        log.error("unexpected error on {} {}", request.method, request.requestURI, exception)
        return render(ProblemKind.INTERNAL, null, request)
    }

    fun render(
        kind: ProblemKind,
        detail: String?,
        request: HttpServletRequest,
        errors: List<FieldProblem> = emptyList(),
        retryAfterSeconds: Long? = null,
    ): ResponseEntity<ByteArray> =
        ResponseEntity
            .status(kind.status)
            .contentType(MediaType.parseMediaType(ProblemBody.MEDIA_TYPE))
            .headers { headers -> retryAfterSeconds?.let { headers.set(HttpHeaders.RETRY_AFTER, it.toString()) } }
            .body(json.writeValueAsBytes(ProblemBody.of(kind, detail, request.requestURI, errors)))
}
