package org.openfsp.gateway.conformance

import org.openfsp.gateway.provider.CallbackRequest
import org.openfsp.gateway.provider.ProviderPayment
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.provider.spi.Providers

class ScriptedProviders(
    private val configured: Providers,
    private val scripts: PaymentScripts,
) : Providers {
    override fun get(id: String): ProviderOperations? = configured[id]?.let { ScriptedProvider(it, scripts) }

    override fun all(): List<ProviderOperations> = configured.all().map { ScriptedProvider(it, scripts) }
}

private class ScriptedProvider(
    private val real: ProviderOperations,
    private val scripts: PaymentScripts,
) : ProviderOperations by real {
    override fun create(request: ProviderPaymentRequest): ProviderPayment = scripts.create(real, request) ?: real.create(request)

    override fun lookup(orderId: String): ProviderReport = scripts.lookup(id, orderId) ?: real.lookup(orderId)

    override fun verifiedCallback(request: CallbackRequest): ProviderReport? = scripts.notified(id, request) ?: real.verifiedCallback(request)

    override fun unsignedCallback(request: CallbackRequest): ProviderReport? = scripts.notified(id, request) ?: real.unsignedCallback(request)
}
