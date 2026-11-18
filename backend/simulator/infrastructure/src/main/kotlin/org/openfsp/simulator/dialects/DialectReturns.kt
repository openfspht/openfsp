package org.openfsp.simulator.dialects

import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.payment.SimulatedPayment

interface DialectReturns {
    val dialect: Dialect

    fun returnTo(
        profile: OperatorProfile,
        payment: SimulatedPayment,
    ): String?
}
