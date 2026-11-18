package org.openfsp.gateway.conformance.spi

import org.openfsp.gateway.provider.CallbackRequest

fun interface CallbackDelivery {
    fun deliver(
        provider: String,
        token: String?,
        request: CallbackRequest,
    )
}
