package org.openfsp.simulator.emission.spi

import org.openfsp.simulator.emission.DeliveryResult
import org.openfsp.simulator.emission.EncodedEvent

interface EventTransport {
    fun deliver(
        operator: String,
        url: String,
        event: EncodedEvent,
        attempt: Int,
    ): DeliveryResult

    fun abandoned(
        url: String,
        event: EncodedEvent,
        attempt: Int,
    )
}
