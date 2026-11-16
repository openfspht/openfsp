package org.openfsp.simulator.operator.space.spi

import org.openfsp.simulator.operator.space.Space
import org.openfsp.simulator.operator.space.SpaceId
import java.time.Instant

interface SpaceRegistry {
    fun open(
        space: Space,
        capacity: Int,
    ): Boolean

    fun find(id: SpaceId): Space?

    fun touch(
        id: SpaceId,
        at: Instant,
    ): Space?

    fun remove(id: SpaceId): Boolean

    fun idleSince(horizon: Instant): List<SpaceId>
}
