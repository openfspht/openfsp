package org.openfsp.simulator.shared.web

import org.openfsp.simulator.control.web.ControlController
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

class ControlProblem(
    val status: HttpStatus,
    detail: String,
) : RuntimeException(detail)

fun <T : Any> T?.orNotFound(detail: String): T = this ?: throw ControlProblem(HttpStatus.NOT_FOUND, detail)

@RestControllerAdvice(assignableTypes = [ControlController::class])
class ControlProblems : ResponseEntityExceptionHandler() {
    @ExceptionHandler(ControlProblem::class)
    fun problem(problem: ControlProblem): ResponseEntity<ProblemDetail> = ResponseEntity.of(ProblemDetail.forStatusAndDetail(problem.status, problem.message)).build()
}
