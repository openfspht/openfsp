package org.openfsp.simulator.dialects

import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.SimulatedPayment

interface DialectReturns {
    val dialect: Dialect

    fun returnTo(
        returnUrl: String,
        payment: SimulatedPayment,
    ): String
}
