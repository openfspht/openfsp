package org.openfsp.simulator.dialects

import org.openfsp.simulator.operator.Caller
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.operator.api.OperatePayments
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

@Component
class DialectGuard(
    private val operations: OperatePayments,
) {
    fun speaks(
        operator: String,
        dialect: Dialect,
    ): Boolean = operations.profile(operator)?.dialect == dialect
}

fun caller(
    expected: String,
    presented: String?,
): Caller = Caller(authenticated = constantTimeEquals(expected, presented), credential = presented.orEmpty())

fun <T, R : Any> OperatorResponse<T>.toEntity(
    answered: (status: Int, body: T) -> ResponseEntity<R>,
    refused: (OperatorResponse.Refused) -> ResponseEntity<R>,
): ResponseEntity<R> =
    when (this) {
        is OperatorResponse.Answered -> answered(status, body)
        is OperatorResponse.Refused -> refused(this)
    }
