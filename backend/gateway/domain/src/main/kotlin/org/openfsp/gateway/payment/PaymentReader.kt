package org.openfsp.gateway.payment

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.payment.api.ReadPayments
import org.openfsp.gateway.payment.spi.Payments
import java.text.Normalizer

@DomainService
class PaymentReader(
    private val payments: Payments,
) : ReadPayments {
    override fun byReference(
        account: String,
        reference: String,
    ): Payment? = payments.withReference(account, Normalizer.normalize(reference, Normalizer.Form.NFC))?.takeIf { it.isVisibleTo(account) }
}
