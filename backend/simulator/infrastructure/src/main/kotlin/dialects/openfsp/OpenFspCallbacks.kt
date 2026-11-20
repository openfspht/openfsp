package org.openfsp.simulator.dialects.openfsp

import org.openfsp.simulator.callbacks.DialectCallbacks
import org.openfsp.simulator.emission.spi.EventEncoder
import org.openfsp.simulator.emission.spi.EventTransport
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.settlement.OperatorCallback
import org.springframework.stereotype.Component

@Component
class OpenFspCallbacks(
    private val encoder: EventEncoder,
    private val transport: EventTransport,
) : DialectCallbacks {
    override val dialect = Dialect.OPENFSP

    override fun send(
        operator: String,
        url: String,
        callback: OperatorCallback,
    ): Boolean = transport.deliver(operator, url, encoder.encode(callback.payment), callback.payment.callbacksSent + 1).succeeded
}
