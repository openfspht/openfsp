package org.openfsp.simulator.space.spi

import org.openfsp.simulator.space.SpaceId
import java.time.Instant

fun interface SpaceCleanup {
    fun clear(space: SpaceId)

    fun purge(
        space: SpaceId,
        before: Instant,
    ) = Unit
}
