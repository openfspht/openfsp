package org.openfsp.simulator.dialects

import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.OperatorCallback

interface DialectCallbacks {
    val dialect: Dialect

    fun send(
        operator: String,
        url: String,
        callback: OperatorCallback,
    ): Boolean
}
