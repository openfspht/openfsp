package org.openfsp.gateway.providers.moncash

import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.CallbackRequest
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.ProviderPayment
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.protocol.NextAction

class MonCashProvider(
    override val id: String,
    override val descriptor: ProviderDescriptor,
    override val limits: AmountLimits,
    private val api: MonCashApi,
    private val mapper: MonCashMapper,
) : ProviderOperations {
    override fun create(request: ProviderPaymentRequest): ProviderPayment =
        mapper.toPayment(request.orderId, api.createPayment(mapper.toCreatePayment(request)))

    override fun lookup(orderId: String): ProviderReport = mapper.toReport(orderId, api.retrieveOrder(MonCashOrderQuery(orderId)))

    override fun returnedOrder(parameters: Map<String, String>): String? = parameters[ORDER_ID_PARAMETER] ?: parameters[ProviderOperations.ORDER_ID]

    override fun transactionOrder(parameters: Map<String, String>): String? {
        val transactionId = parameters[TRANSACTION_ID_PARAMETER] ?: return null
        return api.retrieveTransaction(MonCashTransactionQuery(transactionId))?.reference
    }

    override fun verifiedCallback(request: CallbackRequest): ProviderReport? = null

    override fun unsignedCallback(request: CallbackRequest): ProviderReport? = null

    companion object {
        private const val ORDER_ID_PARAMETER = "orderId"
        private const val TRANSACTION_ID_PARAMETER = "transactionId"

        val DESCRIPTOR =
            ProviderDescriptor(
                displayName = "MonCash",
                capabilities = setOf(ProviderDescriptor.PAYMENTS, ProviderDescriptor.LOOKUP),
                nextActions = setOf(NextAction.Type.REDIRECT),
                payerRequired = false,
                returnUrlRequired = true,
                idempotentCreation = false,
                lookupNotifications = true,
            )
    }
}
