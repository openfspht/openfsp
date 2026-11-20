package org.openfsp.simulator.dialects.natcash

import org.openfsp.simulator.dialects.DialectReturns
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.scenario.Scenario
import org.openfsp.simulator.shared.configuration.SimulatorProperties
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class NatCashReturns(
    private val mapper: NatCashMapper,
    private val signatures: NatCashSignatures,
    private val properties: SimulatorProperties,
) : DialectReturns {
    override val dialect = Dialect.NATCASH

    override fun returnTo(
        profile: OperatorProfile,
        payment: SimulatedPayment,
    ): String? {
        if (payment.scenario == Scenario.NO_CALLBACK) return null
        val callbackUrl = payment.callbackUrl ?: return profile.returnUrl?.let { UriComponentsBuilder.fromUriString(it).queryParam(ORDER_ID, payment.reference).build().toUriString() }
        val code = mapper.responseCodeOf(payment.outcome?.wire ?: PENDING)
        return UriComponentsBuilder
            .fromUriString(callbackUrl)
            .queryParam(ORDER_NUMBER, payment.reference)
            .queryParam(CODE, code)
            .queryParam(TRANS_ID, payment.paymentId)
            .queryParam(SIGNATURE, signatures.forCallback(properties.operator(profile.id), payment.reference, code))
            .build()
            .toUriString()
    }

    private companion object {
        const val ORDER_ID = "order_id"
        const val ORDER_NUMBER = "orderNumber"
        const val CODE = "code"
        const val TRANS_ID = "transId"
        const val SIGNATURE = "signature"
        const val PENDING = "PENDING"
    }
}
