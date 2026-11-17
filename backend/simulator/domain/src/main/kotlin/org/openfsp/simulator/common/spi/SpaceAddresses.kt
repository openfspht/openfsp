package org.openfsp.simulator.common.spi

import org.openfsp.simulator.space.SpaceId

fun interface SpaceAddresses {
    fun publicBaseUrl(space: SpaceId): String
}
