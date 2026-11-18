package org.openfsp.simulator.statement.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.simulator.statement.StatementLine
import org.openfsp.simulator.statement.spi.StatementBook
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Stub
class InMemoryStatementBook : StatementBook {
    private val lines = ConcurrentHashMap<Pair<String, LocalDate>, CopyOnWriteArrayList<StatementLine>>()
    private val closed: MutableSet<Pair<String, LocalDate>> = ConcurrentHashMap.newKeySet()

    override fun book(
        operator: String,
        day: LocalDate,
        line: StatementLine,
    ) {
        lines.computeIfAbsent(operator to day) { CopyOnWriteArrayList() } += line
    }

    override fun close(
        operator: String,
        day: LocalDate,
    ) {
        closed += operator to day
    }

    override fun isClosed(
        operator: String,
        day: LocalDate,
    ): Boolean = closed.contains(operator to day)

    override fun closedDays(operator: String): List<LocalDate> = closed.filter { it.first == operator }.map { it.second }.sortedDescending()

    override fun lines(
        operator: String,
        day: LocalDate,
    ): List<StatementLine> = lines[operator to day].orEmpty().toList()

    override fun clear() {
        lines.clear()
        closed.clear()
    }
}
