package org.openfsp.simulator.dialects.natcash

import org.openfsp.simulator.callbacks.DialectCallbacks
import org.openfsp.simulator.callbacks.OperatorWebhookClient
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.settlement.OperatorCallback
import org.openfsp.simulator.shared.configuration.SimulatorProperties
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

@Component
class NatCashCallbacks(
    private val client: OperatorWebhookClient,
    private val mapper: NatCashMapper,
    private val signatures: NatCashSignatures,
    private val json: JsonMapper,
    private val properties: SimulatorProperties,
) : DialectCallbacks {
    override val dialect = Dialect.NATCASH

    override fun send(
        operator: String,
        url: String,
        callback: OperatorCallback,
    ): Boolean {
        val code = mapper.responseCodeOf(callback.status)
        val signature = signatures.forCallback(properties.operator(operator), callback.reference, code)
        return client.post(url, json.writeValueAsBytes(NatCashCallback(callback.reference, code, callback.paymentId, signature)))
    }
}
