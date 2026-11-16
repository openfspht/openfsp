package org.openfsp.simulator.operator.spi

import org.openfsp.simulator.operator.space.SpaceId

interface Sequences {
    fun next(
        space: SpaceId,
        counter: String,
    ): Long

    fun clear(space: SpaceId)
}
