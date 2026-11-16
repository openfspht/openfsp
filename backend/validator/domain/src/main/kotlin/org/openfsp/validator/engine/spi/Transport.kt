package org.openfsp.validator.engine.spi

import org.openfsp.validator.engine.Exchange
import org.openfsp.validator.engine.OutgoingRequest

fun interface Transport {
    fun send(request: OutgoingRequest): Exchange
}
