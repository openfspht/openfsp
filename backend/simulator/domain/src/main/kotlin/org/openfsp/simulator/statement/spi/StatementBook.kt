package org.openfsp.simulator.statement.spi

import org.openfsp.simulator.space.SpaceId
import org.openfsp.simulator.statement.StatementLine
import java.time.LocalDate

interface StatementBook {
    fun book(
        space: SpaceId,
        operator: String,
        day: LocalDate,
        line: StatementLine,
    )

    fun close(
        space: SpaceId,
        operator: String,
        day: LocalDate,
    )

    fun isClosed(
        space: SpaceId,
        operator: String,
        day: LocalDate,
    ): Boolean

    fun closedDays(
        space: SpaceId,
        operator: String,
    ): List<LocalDate>

    fun lines(
        space: SpaceId,
        operator: String,
        day: LocalDate,
    ): List<StatementLine>

    fun clear(space: SpaceId)
}
