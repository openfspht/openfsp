package org.openfsp.simulator.scenario.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.simulator.scenario.Scenario
import org.openfsp.simulator.scenario.spi.ScenarioBook
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Stub
class InMemoryScenarioBook : ScenarioBook {
    private val declared = ConcurrentHashMap<Declared, Scenario>()
    private val defaults = ConcurrentHashMap<String, Scenario>()
    private val cursors = ConcurrentHashMap<String, AtomicInteger>()

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
        account: String,
        reference: String,
        rotation: List<Scenario>,
    ): Scenario {
        val key = Declared(operator, account, reference)
        val cursor = cursors[operator]
        if (cursor == null || rotation.isEmpty()) return declared[key] ?: defaultFor(operator)
        return declared.computeIfAbsent(key) { rotation[Math.floorMod(cursor.getAndIncrement(), rotation.size)] }
    }

    override fun clear() {
        declared.clear()
        defaults.clear()
        cursors.clear()
    }
}

private data class Declared(
    val operator: String,
    val account: String,
    val reference: String,
)
