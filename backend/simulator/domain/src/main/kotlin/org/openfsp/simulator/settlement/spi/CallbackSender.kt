package org.openfsp.simulator.settlement.spi

import org.openfsp.simulator.settlement.OperatorCallback

fun interface CallbackSender {
    fun send(
        operator: String,
        url: String,
        callback: OperatorCallback,
    ): Boolean
}
