package org.openfsp.simulator.statement

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class StatementLine(
    val reference: String,
    val orderId: String?,
    val type: String,
    val amount: BigDecimal,
    val bookedAt: LocalDateTime,
) {
    init {
        require(amount.stripTrailingZeros().scale() <= CENT_DIGITS) { "a statement amount has at most $CENT_DIGITS decimals" }
    }

    private companion object {
        const val CENT_DIGITS = 2
    }
}

data class OperatorStatement(
    val businessDate: LocalDate,
    val closed: Boolean,
    val entries: List<StatementLine>,
)

data class StatementDays(
    val today: LocalDate,
    val closed: List<LocalDate>,
)
