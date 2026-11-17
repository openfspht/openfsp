package org.openfsp.simulator.memory

import org.openfsp.simulator.scenario.Scenario
import org.openfsp.simulator.scenario.spi.ScenarioBook
import org.openfsp.simulator.space.SpaceId
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Component
class MemoryScenarioBook : ScenarioBook {
    private class Book {
        val declared = ConcurrentHashMap<Pair<String, String>, Scenario>()
        val defaults = ConcurrentHashMap<String, Scenario>()
        val cursors = ConcurrentHashMap<String, AtomicInteger>()
    }

    private val books = ConcurrentHashMap<SpaceId, Book>()

    private fun of(space: SpaceId) = books.computeIfAbsent(space) { Book() }

    override fun declare(
        space: SpaceId,
        operator: String,
        orderId: String,
        scenario: Scenario,
    ) {
        of(space).declared[operator to orderId] = scenario
    }

    override fun declareDefault(
        space: SpaceId,
        operator: String,
        scenario: Scenario,
    ) {
        of(space).defaults[operator] = scenario
    }

    override fun defaultFor(
        space: SpaceId,
        operator: String,
    ): Scenario = books[space]?.defaults?.get(operator) ?: Scenario.SUCCESS

    override fun rotate(
        space: SpaceId,
        operator: String,
        enabled: Boolean,
    ) {
        val cursors = of(space).cursors
        if (enabled) cursors.putIfAbsent(operator, AtomicInteger()) else cursors.remove(operator)
    }

    override fun rotates(
        space: SpaceId,
        operator: String,
    ): Boolean = books[space]?.cursors?.containsKey(operator) == true

    override fun scenarioFor(
        space: SpaceId,
        operator: String,
        orderId: String,
        rotation: List<Scenario>,
    ): Scenario {
        val book = of(space)
        val cursor = book.cursors[operator]
        if (cursor == null || rotation.isEmpty()) return book.declared[operator to orderId] ?: defaultFor(space, operator)
        return book.declared.computeIfAbsent(operator to orderId) { rotation[Math.floorMod(cursor.getAndIncrement(), rotation.size)] }
    }

    override fun clear(space: SpaceId) {
        books.remove(space)
    }
}
