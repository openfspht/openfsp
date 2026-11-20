package org.openfsp.simulator.dialects

import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.payment.api.OperatePayments
import org.springframework.stereotype.Component

@Component
class DialectGuard(
    private val operations: OperatePayments,
) {
    fun profileOf(
        operator: String,
        dialect: Dialect,
    ): OperatorProfile? = operations.profile(operator)?.takeIf { it.dialect == dialect }

    fun speaks(
        operator: String,
        dialect: Dialect,
    ): Boolean = profileOf(operator, dialect) != null
}
