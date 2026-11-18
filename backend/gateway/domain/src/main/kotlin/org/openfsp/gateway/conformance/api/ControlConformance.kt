package org.openfsp.gateway.conformance.api

import org.openfsp.gateway.conformance.ControlledPayment

interface ControlConformance {
    fun declare(
        principal: String,
        reference: String,
        scenario: String?,
    )

    fun state(
        principal: String,
        reference: String,
    ): ControlledPayment

    fun resolve(
        principal: String,
        paymentId: String,
        outcome: String?,
        reason: String?,
    )

    fun callback(
        principal: String,
        paymentId: String,
    )
}
