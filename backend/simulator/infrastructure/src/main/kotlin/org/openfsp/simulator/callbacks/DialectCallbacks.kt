package org.openfsp.simulator.callbacks

import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.settlement.OperatorCallback

interface DialectCallbacks {
    val dialect: Dialect

    fun send(
        operator: String,
        url: String,
        callback: OperatorCallback,
    ): Boolean
}
