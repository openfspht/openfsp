package org.openfsp.gateway.payment.api

import org.openfsp.gateway.payment.Payment

interface SynchronizePayment {
    fun current(
        account: String,
        paymentId: String,
    ): Payment
}
