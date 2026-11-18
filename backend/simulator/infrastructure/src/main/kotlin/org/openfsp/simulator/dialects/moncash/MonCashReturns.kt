package org.openfsp.simulator.dialects.moncash

import org.openfsp.simulator.dialects.DialectReturns
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.payment.SimulatedPayment
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class MonCashReturns : DialectReturns {
    override val dialect = Dialect.MONCASH

    override fun returnTo(
        profile: OperatorProfile,
        payment: SimulatedPayment,
    ): String? = profile.returnUrl?.let { UriComponentsBuilder.fromUriString(it).queryParam("transactionId", payment.paymentId).build().toUriString() }
}
