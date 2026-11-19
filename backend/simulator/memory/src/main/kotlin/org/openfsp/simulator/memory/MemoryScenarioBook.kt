package org.openfsp.simulator.memory

import org.openfsp.simulator.scenario.Scenario
import org.openfsp.simulator.scenario.spi.ScenarioBook
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class MemoryScenarioBook : ScenarioBook {
    private val declared = ConcurrentHashMap<Declared, Scenario>()
    private val defaults = ConcurrentHashMap<Book, Scenario>()
    private val cursors = ConcurrentHashMap<Book, AtomicInteger>()

    override fun declare(
        operator: String,
        account: String,
        reference: String,
        scenario: Scenario,
    ) {
        declared[Declared(operator, account, reference)] = scenario
    }

    override fun declareDefault(
        operator: String,
        account: String,
        scenario: Scenario,
    ) {
        defaults[Book(operator, account)] = scenario
    }

    override fun defaultFor(
        operator: String,
        account: String,
    ): Scenario = defaults[Book(operator, account)] ?: Scenario.SUCCESS

    override fun rotate(
        operator: String,
        account: String,
        enabled: Boolean,
    ) {
        val book = Book(operator, account)
        if (enabled) cursors.putIfAbsent(book, AtomicInteger()) else cursors.remove(book)
    }

    override fun rotates(
        operator: String,
        account: String,
    ): Boolean = cursors.containsKey(Book(operator, account))

    override fun scenarioFor(
        operator: String,
        account: String,
        reference: String,
        rotation: List<Scenario>,
    ): Scenario {
        val key = Declared(operator, account, reference)
        val cursor = cursors[Book(operator, account)]
        if (cursor == null || rotation.isEmpty()) return declared[key] ?: defaultFor(operator, account)
        return declared.computeIfAbsent(key) { rotation[Math.floorMod(cursor.getAndIncrement(), rotation.size)] }
    }

    override fun clear() {
        declared.clear()
        defaults.clear()
        cursors.clear()
    }

    override fun clear(account: String) {
        declared.keys.removeIf { it.account == account }
        defaults.keys.removeIf { it.account == account }
        cursors.keys.removeIf { it.account == account }
    }
}

private data class Declared(
    val operator: String,
    val account: String,
    val reference: String,
)

private data class Book(
    val operator: String,
    val account: String,
)
