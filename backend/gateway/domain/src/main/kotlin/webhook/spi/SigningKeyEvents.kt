package org.openfsp.gateway.webhook.spi

fun interface SigningKeyEvents {
    fun rotated(kid: String)
}
