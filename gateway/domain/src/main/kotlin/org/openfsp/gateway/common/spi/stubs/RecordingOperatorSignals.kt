package org.openfsp.gateway.common.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.common.spi.OperatorSignals

@Stub
class RecordingOperatorSignals : OperatorSignals {
    val signals = mutableListOf<Pair<String, String>>()

    override fun signal(
        subject: String,
        message: String,
    ) {
        signals += subject to message
    }
}
