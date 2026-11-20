package org.openfsp.simulator.shared.web

import org.openfsp.simulator.control.web.ControlController
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

class ControlProblem(
    val status: HttpStatus,
    detail: String,
    val headers: Map<String, String> = emptyMap(),
) : RuntimeException(detail)

fun ControlProblem.toResponse(): ResponseEntity<ProblemDetail> =
    ResponseEntity
        .status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .headers { http -> headers.forEach { (name, value) -> http.set(name, value) } }
        .body(ProblemDetail.forStatusAndDetail(status, message))

fun <T : Any> T?.orNotFound(detail: String): T = this ?: throw ControlProblem(HttpStatus.NOT_FOUND, detail)

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = [ControlController::class])
class ControlProblems : ResponseEntityExceptionHandler() {
    @ExceptionHandler(ControlProblem::class)
    fun problem(problem: ControlProblem): ResponseEntity<ProblemDetail> = problem.toResponse()
}
