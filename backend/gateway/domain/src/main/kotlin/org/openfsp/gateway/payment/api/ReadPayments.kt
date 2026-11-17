package org.openfsp.gateway.payment.api

import org.openfsp.gateway.payment.Payment

interface ReadPayments {
    fun find(paymentId: String): Payment

    fun byReference(reference: String): Payment?
}
