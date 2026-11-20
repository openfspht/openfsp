package org.openfsp.gateway.payment.api

import org.openfsp.gateway.payment.CreatePaymentCommand
import org.openfsp.gateway.payment.Payment

interface CreatePayment {
    fun create(command: CreatePaymentCommand): Payment
}
