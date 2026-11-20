package org.openfsp.simulator.common.spi

fun interface PublicAddress {
    fun baseUrl(): String

    fun checkoutUrl(
        operator: String,
        paymentId: String,
    ): String = "${baseUrl()}/$operator/checkout/$paymentId"
}
