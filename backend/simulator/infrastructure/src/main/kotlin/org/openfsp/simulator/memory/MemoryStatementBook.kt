package org.openfsp.simulator.memory

import org.openfsp.simulator.space.SpaceId
import org.openfsp.simulator.statement.StatementLine
import org.openfsp.simulator.statement.spi.StatementBook
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
class MemoryStatementBook : StatementBook {
    private class Book {
        val lines = ConcurrentHashMap<Pair<String, LocalDate>, CopyOnWriteArrayList<StatementLine>>()
        val closed: MutableSet<Pair<String, LocalDate>> = ConcurrentHashMap.newKeySet()
    }

    private val books = ConcurrentHashMap<SpaceId, Book>()

    private fun of(space: SpaceId) = books.computeIfAbsent(space) { Book() }

    override fun book(
        space: SpaceId,
        operator: String,
        day: LocalDate,
        line: StatementLine,
    ) {
        of(space).lines.computeIfAbsent(operator to day) { CopyOnWriteArrayList() } += line
    }

    override fun close(
        space: SpaceId,
        operator: String,
        day: LocalDate,
    ) {
        of(space).closed += operator to day
    }

    override fun isClosed(
        space: SpaceId,
        operator: String,
        day: LocalDate,
    ): Boolean = books[space]?.closed?.contains(operator to day) == true

    override fun closedDays(
        space: SpaceId,
        operator: String,
    ): List<LocalDate> =
        books[space]
            ?.closed
            .orEmpty()
            .filter { it.first == operator }
            .map { it.second }
            .sortedDescending()

    override fun lines(
        space: SpaceId,
        operator: String,
        day: LocalDate,
    ): List<StatementLine> = books[space]?.lines?.get(operator to day).orEmpty().toList()

    override fun clear(space: SpaceId) {
        books.remove(space)
    }
}
