package org.openfsp.gateway.providers.natcash

import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.CallbackRequest
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.ProviderPayment
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.VerifiedCallback
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.protocol.NextAction

class NatCashProvider(
    override val id: String,
    override val descriptor: ProviderDescriptor,
    override val limits: AmountLimits,
    private val api: NatCashApi,
    private val mapper: NatCashMapper,
    private val signatures: NatCashSignatures,
) : ProviderOperations {
    override fun create(request: ProviderPaymentRequest): ProviderPayment =
        mapper.toPayment(request.orderId, api.requestPayment(mapper.toPaymentRequest(request)))

    override fun lookup(orderId: String): ProviderReport = mapper.toReport(orderId, api.checkTransaction(mapper.toTransactionQuery(orderId)))

    override fun verifiedCallback(request: CallbackRequest): VerifiedCallback? {
        val callback = mapper.toCallback(request)?.takeIf(signatures::isAuthentic) ?: return null
        return mapper.toReport(callback)?.let(VerifiedCallback::Reported)
    }

    override fun unsignedCallback(request: CallbackRequest): ProviderReport? = null

    override fun returnedOrder(parameters: Map<String, String>): String? = parameters[ORDER_NUMBER] ?: parameters[ProviderOperations.ORDER_ID]

    companion object {
        const val PROVIDER = "NatCash"
        private const val ORDER_NUMBER = "orderNumber"

        val DESCRIPTOR =
            ProviderDescriptor(
                displayName = PROVIDER,
                capabilities = setOf(ProviderDescriptor.PAYMENTS, ProviderDescriptor.LOOKUP),
                nextActions = setOf(NextAction.Type.REDIRECT),
                payerRequired = false,
                locales = setOf("en", "fr", "ht"),
                idempotentCreation = false,
                signedCallbacks = true,
                paymentUrl = true,
                reportsAmount = true,
            )
    }
}
