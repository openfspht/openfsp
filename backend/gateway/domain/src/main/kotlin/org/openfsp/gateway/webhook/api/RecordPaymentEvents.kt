package org.openfsp.gateway.webhook.api

import org.openfsp.gateway.payment.PaymentTransitioned

interface RecordPaymentEvents {
    fun record(transition: PaymentTransitioned)
}
