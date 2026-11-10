package org.openfsp.simulator.dialects

import org.openfsp.simulator.operator.settlement.OperatorCallback
import org.openfsp.simulator.operator.settlement.spi.CallbackSender
import org.openfsp.simulator.operator.spi.OperatorProfiles
import org.springframework.stereotype.Component

@Component
class OperatorCallbackSender(
    private val profiles: OperatorProfiles,
    senders: List<DialectCallbacks>,
) : CallbackSender {
    private val byDialect = senders.associateBy { it.dialect }

    override fun send(
        operator: String,
        url: String,
        callback: OperatorCallback,
    ): Boolean {
        val dialect = profiles[operator]?.dialect ?: return false
        return byDialect[dialect]?.send(operator, url, callback) ?: false
    }
}
