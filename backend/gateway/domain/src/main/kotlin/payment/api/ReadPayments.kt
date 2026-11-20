package org.openfsp.gateway.payment.api

import org.openfsp.gateway.payment.Payment

interface ReadPayments {
    fun byReference(
        account: String,
        reference: String,
    ): Payment?
}
