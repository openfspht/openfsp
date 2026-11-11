package org.openfsp.gateway.payment.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.payment.PaymentTransitioned
import org.openfsp.gateway.payment.spi.PaymentEvents

@Stub
class RecordingPaymentEvents : PaymentEvents {
    val published = mutableListOf<PaymentTransitioned>()

    override fun publish(event: PaymentTransitioned) {
        published += event
    }
}
