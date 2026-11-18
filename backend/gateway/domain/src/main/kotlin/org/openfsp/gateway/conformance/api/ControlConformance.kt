package org.openfsp.gateway.conformance.api

import org.openfsp.gateway.conformance.ControlledPayment

interface ControlConformance {
    fun declare(
        account: String,
        principal: String,
        reference: String,
        scenario: String?,
    )

    fun state(
        account: String,
        principal: String,
        reference: String,
    ): ControlledPayment

    fun resolve(
        account: String,
        principal: String,
        paymentId: String,
        outcome: String?,
        reason: String?,
    )

    fun callback(
        account: String,
        principal: String,
        paymentId: String,
    )
}
