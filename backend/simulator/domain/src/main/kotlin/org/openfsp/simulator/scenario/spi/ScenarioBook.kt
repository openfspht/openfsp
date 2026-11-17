package org.openfsp.simulator.scenario.spi

import org.openfsp.simulator.scenario.Scenario
import org.openfsp.simulator.space.SpaceId

interface ScenarioBook {
    fun declare(
        space: SpaceId,
        operator: String,
        orderId: String,
        scenario: Scenario,
    )

    fun declareDefault(
        space: SpaceId,
        operator: String,
        scenario: Scenario,
    )

    fun defaultFor(
        space: SpaceId,
        operator: String,
    ): Scenario

    fun rotate(
        space: SpaceId,
        operator: String,
        enabled: Boolean,
    )

    fun rotates(
        space: SpaceId,
        operator: String,
    ): Boolean

    fun scenarioFor(
        space: SpaceId,
        operator: String,
        orderId: String,
        rotation: List<Scenario>,
    ): Scenario

    fun clear(space: SpaceId)
}
