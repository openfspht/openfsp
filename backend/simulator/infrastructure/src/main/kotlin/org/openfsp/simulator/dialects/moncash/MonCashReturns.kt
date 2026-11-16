package org.openfsp.simulator.dialects.moncash

import org.openfsp.simulator.dialects.DialectReturns
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.payment.SimulatedPayment
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class MonCashReturns : DialectReturns {
    override val dialect = Dialect.MONCASH

    override fun returnTo(
        returnUrl: String,
        payment: SimulatedPayment,
    ): String = UriComponentsBuilder.fromUriString(returnUrl).queryParam("transactionId", payment.paymentId).build().toUriString()
}
