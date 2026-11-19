package org.openfsp.gateway.conformance

import org.openfsp.protocol.api.ConformanceFixturesResource
import org.openfsp.protocol.api.ControlledPaymentResource
import org.springframework.stereotype.Component

@Component
class ConformanceResourceMapper {
    fun toResource(fixtures: ConformanceFixtures): ConformanceFixturesResource = ConformanceFixturesResource(fixtures.apiKey, fixtures.expiresAt, fixtures.inputs, fixtures.controlEndpoint.toString())

    fun toResource(payment: ControlledPayment): ControlledPaymentResource = ControlledPaymentResource(payment.paymentId, payment.settled, payment.callbacksSent)
}
