package org.openfsp.gateway.payment

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.payment.api.ReadPayments
import org.openfsp.gateway.payment.spi.Payments
import java.text.Normalizer

@DomainService
class PaymentReader(
    private val payments: Payments,
) : ReadPayments {
    override fun find(paymentId: String): Payment = payments.find(paymentId)?.takeIf(Payment::isVisible) ?: throw OpenFspException.notFound()

    override fun byReference(reference: String): Payment? = payments.withReference(Normalizer.normalize(reference, Normalizer.Form.NFC))?.takeIf(Payment::isVisible)
}
