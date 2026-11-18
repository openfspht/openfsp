package org.openfsp.simulator.memory

import org.openfsp.simulator.scenario.Scenario
import org.openfsp.simulator.scenario.spi.ScenarioBook
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Component
class MemoryScenarioBook : ScenarioBook {
    private val declared = ConcurrentHashMap<Pair<String, String>, Scenario>()
    private val defaults = ConcurrentHashMap<String, Scenario>()
    private val cursors = ConcurrentHashMap<String, AtomicInteger>()

    override fun declare(
        operator: String,
        reference: String,
        scenario: Scenario,
    ) {
        declared[operator to reference] = scenario
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
        reference: String,
        rotation: List<Scenario>,
    ): Scenario {
        val cursor = cursors[operator]
        if (cursor == null || rotation.isEmpty()) return declared[operator to reference] ?: defaultFor(operator)
        return declared.computeIfAbsent(operator to reference) { rotation[Math.floorMod(cursor.getAndIncrement(), rotation.size)] }
    }

    override fun clear() {
        declared.clear()
        defaults.clear()
        cursors.clear()
    }
}
