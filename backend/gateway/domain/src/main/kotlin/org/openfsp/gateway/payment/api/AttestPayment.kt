package org.openfsp.gateway.payment.api

import org.openfsp.gateway.payment.Attestation
import org.openfsp.gateway.payment.Payment

interface AttestPayment {
    fun attest(
        paymentId: String,
        attestation: Attestation,
    ): Payment
}
