package org.openfsp.simulator.scenario.spi

import org.openfsp.simulator.scenario.Scenario

interface ScenarioBook {
    fun declare(
        operator: String,
        account: String,
        reference: String,
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
        account: String,
        reference: String,
        rotation: List<Scenario>,
    ): Scenario

    fun clear()
}
