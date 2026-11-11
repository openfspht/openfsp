package org.openfsp.simulator.operator.scenario.spi

import org.openfsp.simulator.operator.scenario.Scenario

interface ScenarioBook {
    fun declare(
        operator: String,
        orderId: String,
        scenario: Scenario,
    )

    fun declareDefault(
        operator: String,
        scenario: Scenario,
    )

    fun defaultFor(operator: String): Scenario

    fun rotate(
        operator: String,
        enabled: Boolean,
    )

    fun rotates(operator: String): Boolean

    fun scenarioFor(
        operator: String,
        orderId: String,
        rotation: List<Scenario>,
    ): Scenario

    fun clear()
}
