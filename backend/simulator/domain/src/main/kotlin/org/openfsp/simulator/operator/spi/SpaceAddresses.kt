package org.openfsp.simulator.operator.spi

import org.openfsp.simulator.operator.space.SpaceId

fun interface SpaceAddresses {
    fun publicBaseUrl(space: SpaceId): String
}
