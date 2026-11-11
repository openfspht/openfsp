package org.openfsp.simulator.operator.scenario.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.simulator.operator.scenario.Scenario
import org.openfsp.simulator.operator.scenario.spi.ScenarioBook
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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
