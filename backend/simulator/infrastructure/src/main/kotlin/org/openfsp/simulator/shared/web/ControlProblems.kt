package org.openfsp.simulator.shared.web

import org.openfsp.simulator.control.web.ControlController
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

fun <T : Any> T?.orNotFound(detail: String): T = this ?: throw ControlProblem(HttpStatus.NOT_FOUND, detail)

@RestControllerAdvice(assignableTypes = [ControlController::class])
class ControlProblems : ResponseEntityExceptionHandler() {
    @ExceptionHandler(ControlProblem::class)
    fun problem(problem: ControlProblem): ResponseEntity<ProblemDetail> =
        ResponseEntity
            .status(problem.status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .headers { headers -> problem.headers.forEach { (name, value) -> headers.set(name, value) } }
            .body(ProblemDetail.forStatusAndDetail(problem.status, problem.message))
}
