package org.openfsp.simulator.common.spi

import org.openfsp.simulator.space.SpaceId

interface Sequences {
    fun next(
        space: SpaceId,
        counter: String,
    ): Long

    fun clear(space: SpaceId)
}
