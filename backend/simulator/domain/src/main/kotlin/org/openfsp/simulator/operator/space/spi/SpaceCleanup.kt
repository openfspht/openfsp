package org.openfsp.simulator.operator.space.spi

import org.openfsp.simulator.operator.space.SpaceId

fun interface SpaceCleanup {
    fun clear(space: SpaceId)
}
