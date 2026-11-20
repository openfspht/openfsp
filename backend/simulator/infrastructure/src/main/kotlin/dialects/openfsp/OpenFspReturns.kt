package org.openfsp.simulator.dialects.openfsp

import org.openfsp.simulator.dialects.DialectReturns
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.payment.SimulatedPayment
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class OpenFspReturns : DialectReturns {
    override val dialect = Dialect.OPENFSP

    override fun returnTo(
        profile: OperatorProfile,
        payment: SimulatedPayment,
    ): String? = profile.returnUrl?.let { UriComponentsBuilder.fromUriString(it).queryParam("order_id", payment.reference).build().toUriString() }
}
