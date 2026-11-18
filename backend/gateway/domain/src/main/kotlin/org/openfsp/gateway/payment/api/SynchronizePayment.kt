package org.openfsp.gateway.payment.api

import org.openfsp.gateway.payment.Payment

interface SynchronizePayment {
    fun synchronize(paymentId: String): Payment

    fun current(paymentId: String): Payment
}
