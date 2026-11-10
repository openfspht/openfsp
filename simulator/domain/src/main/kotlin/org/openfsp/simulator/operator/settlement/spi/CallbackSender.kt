package org.openfsp.simulator.operator.settlement.spi

import org.openfsp.simulator.operator.settlement.OperatorCallback

fun interface CallbackSender {
    fun send(
        operator: String,
        url: String,
        callback: OperatorCallback,
    ): Boolean
}
