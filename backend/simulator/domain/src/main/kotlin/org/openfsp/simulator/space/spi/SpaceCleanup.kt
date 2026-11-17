package org.openfsp.simulator.space.spi

import org.openfsp.simulator.space.SpaceId

fun interface SpaceCleanup {
    fun clear(space: SpaceId)
}
