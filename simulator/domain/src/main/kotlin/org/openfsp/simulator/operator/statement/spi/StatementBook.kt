package org.openfsp.simulator.operator.statement.spi

import org.openfsp.simulator.operator.statement.StatementLine
import java.time.LocalDate

interface StatementBook {
    fun book(
        operator: String,
        day: LocalDate,
        line: StatementLine,
    )

    fun close(
        operator: String,
        day: LocalDate,
    )

    fun isClosed(
        operator: String,
        day: LocalDate,
    ): Boolean

    fun closedDays(operator: String): List<LocalDate>

    fun lines(
        operator: String,
        day: LocalDate,
    ): List<StatementLine>

    fun clear()
}
