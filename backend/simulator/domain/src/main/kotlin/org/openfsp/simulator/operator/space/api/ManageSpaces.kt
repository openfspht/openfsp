package org.openfsp.simulator.operator.space.api

import org.openfsp.simulator.operator.space.Space
import org.openfsp.simulator.operator.space.SpaceId
import java.time.Duration

interface ManageSpaces {
    val idleLifetime: Duration

    fun open(): Space?

    fun enter(id: SpaceId): Space?

    fun find(id: SpaceId): Space?

    fun close(id: SpaceId): Boolean

    fun expireIdle(): List<SpaceId>
}
