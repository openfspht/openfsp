package org.openfsp.gateway.payment.spi

import org.openfsp.gateway.payment.PaymentTransitioned

interface PaymentEvents {
    fun publish(event: PaymentTransitioned)
}
