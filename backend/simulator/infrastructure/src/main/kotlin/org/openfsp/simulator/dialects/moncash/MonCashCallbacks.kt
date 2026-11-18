package org.openfsp.simulator.dialects.moncash

import org.openfsp.simulator.callbacks.DialectCallbacks
import org.openfsp.simulator.callbacks.OperatorWebhookClient
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.settlement.OperatorCallback
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class MonCashCallbacks(
    private val client: OperatorWebhookClient,
) : DialectCallbacks {
    override val dialect = Dialect.MONCASH

    override fun send(
        operator: String,
        url: String,
        callback: OperatorCallback,
    ): Boolean = client.get(UriComponentsBuilder.fromUriString(url).queryParam(TRANSACTION_ID, callback.paymentId).build().toUriString())

    private companion object {
        const val TRANSACTION_ID = "transactionId"
    }
}
