package org.openfsp.simulator.space.api

import org.openfsp.simulator.space.Space
import org.openfsp.simulator.space.SpaceId
import java.time.Duration

interface ManageSpaces {
    val idleLifetime: Duration

    fun open(): Space?

    fun enter(id: SpaceId): Space?

    fun find(id: SpaceId): Space?

    fun close(id: SpaceId): Boolean

    fun expireIdle(): List<SpaceId>
}
