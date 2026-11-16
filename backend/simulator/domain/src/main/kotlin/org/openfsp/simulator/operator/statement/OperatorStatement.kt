package org.openfsp.simulator.operator.statement

import java.time.LocalDate

data class StatementLine(
    val reference: String,
    val orderId: String?,
    val type: String,
    val amount: String,
    val bookedAt: String,
)

data class OperatorStatement(
    val businessDate: LocalDate,
    val closed: Boolean,
    val entries: List<StatementLine>,
)

data class StatementDays(
    val today: LocalDate,
    val closed: List<LocalDate>,
)
