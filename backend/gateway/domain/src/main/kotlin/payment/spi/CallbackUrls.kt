package org.openfsp.gateway.payment.spi

import java.net.URI

fun interface CallbackUrls {
    fun forProvider(
        providerId: String,
        token: String?,
    ): URI
}
