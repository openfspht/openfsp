package org.openfsp.simulator.dialects

import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.payment.api.OperatePayments
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
