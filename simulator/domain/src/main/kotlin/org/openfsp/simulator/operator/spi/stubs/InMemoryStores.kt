package org.openfsp.simulator.operator.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.simulator.operator.JournalEntry
import org.openfsp.simulator.operator.Scenario
import org.openfsp.simulator.operator.SimulatedPayment
import org.openfsp.simulator.operator.StatementLine
import org.openfsp.simulator.operator.spi.RequestJournal
import org.openfsp.simulator.operator.spi.ScenarioBook
import org.openfsp.simulator.operator.spi.SimulatedPayments
import org.openfsp.simulator.operator.spi.StatementBook
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@Stub
class InMemorySimulatedPayments : SimulatedPayments {
    private val payments = ConcurrentHashMap<String, SimulatedPayment>()

    override fun save(payment: SimulatedPayment): SimulatedPayment = payment.also { payments[it.paymentId] = it }

    override fun find(paymentId: String): SimulatedPayment? = payments[paymentId]

    override fun withOrder(
        operator: String,
        orderId: String,
    ): SimulatedPayment? = payments.values.firstOrNull { it.operator == operator && it.orderId == orderId }

    override fun update(
        paymentId: String,
        change: (SimulatedPayment) -> SimulatedPayment,
    ): SimulatedPayment? = payments.computeIfPresent(paymentId) { _, current -> change(current) }

    override fun forget(paymentId: String) {
        payments.remove(paymentId)
    }

    override fun all(): List<SimulatedPayment> = payments.values.sortedByDescending { it.createdAt }

    override fun clear() = payments.clear()
}

@Stub
class InMemoryScenarioBook : ScenarioBook {
    private val declared = ConcurrentHashMap<Pair<String, String>, Scenario>()
    private val defaults = ConcurrentHashMap<String, Scenario>()
    private val cursors = ConcurrentHashMap<String, AtomicInteger>()

    override fun declare(
        operator: String,
        orderId: String,
        scenario: Scenario,
    ) {
        declared[operator to orderId] = scenario
    }

    override fun declareDefault(
        operator: String,
        scenario: Scenario,
    ) {
        defaults[operator] = scenario
    }

    override fun defaultFor(operator: String): Scenario = defaults[operator] ?: Scenario.SUCCESS

    override fun rotate(
        operator: String,
        enabled: Boolean,
    ) {
        if (enabled) cursors.putIfAbsent(operator, AtomicInteger()) else cursors.remove(operator)
    }

    override fun rotates(operator: String): Boolean = cursors.containsKey(operator)

    override fun scenarioFor(
        operator: String,
        orderId: String,
        rotation: List<Scenario>,
    ): Scenario {
        val cursor = cursors[operator]
        if (cursor == null || rotation.isEmpty()) return declared[operator to orderId] ?: defaultFor(operator)
        return declared.computeIfAbsent(operator to orderId) { rotation[Math.floorMod(cursor.getAndIncrement(), rotation.size)] }
    }

    override fun clear() {
        declared.clear()
        defaults.clear()
        cursors.clear()
    }
}

@Stub
class InMemoryRequestJournal : RequestJournal {
    private val entries = CopyOnWriteArrayList<JournalEntry>()

    override fun record(entry: JournalEntry) {
        entries += entry
    }

    override fun entries(operator: String?): List<JournalEntry> = entries.filter { operator == null || it.operator == operator }

    override fun clear() = entries.clear()
}

@Stub
class InMemoryStatementBook : StatementBook {
    private val lines = ConcurrentHashMap<Pair<String, LocalDate>, CopyOnWriteArrayList<StatementLine>>()
    private val closed = ConcurrentHashMap.newKeySet<Pair<String, LocalDate>>()

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
    ): Boolean = (operator to day) in closed

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
