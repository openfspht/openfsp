package org.openfsp.gateway.webhook.api

import org.openfsp.gateway.payment.PaymentTransitioned

fun interface RecordPaymentEvents {
    fun record(transition: PaymentTransitioned)
}
