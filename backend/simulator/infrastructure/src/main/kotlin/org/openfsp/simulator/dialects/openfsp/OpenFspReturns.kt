package org.openfsp.simulator.dialects.openfsp

import org.openfsp.simulator.dialects.DialectReturns
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.payment.SimulatedPayment
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class OpenFspReturns : DialectReturns {
    override val dialect = Dialect.OPENFSP

    override fun returnTo(
        returnUrl: String,
        payment: SimulatedPayment,
    ): String = UriComponentsBuilder.fromUriString(returnUrl).queryParam("order_id", payment.orderId).build().toUriString()
}
