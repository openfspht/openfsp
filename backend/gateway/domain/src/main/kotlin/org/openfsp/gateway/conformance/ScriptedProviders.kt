package org.openfsp.gateway.conformance

import org.openfsp.gateway.provider.CallbackRequest
import org.openfsp.gateway.provider.ProviderPayment
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.RevisionOperations
import org.openfsp.gateway.provider.VerifiedCallback
import org.openfsp.gateway.provider.ServedProvider
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.provider.spi.Providers

class ScriptedProviders(
    private val configured: Providers,
    private val scripts: PaymentScripts,
) : Providers {
    override fun served(): List<ServedProvider> = configured.served()

    override fun platform(id: String): ProviderOperations? = configured.platform(id)?.let { ScriptedProvider(it, scripts) }

    override fun active(
        account: String,
        id: String,
    ): RevisionOperations? = configured.active(account, id)?.let { it.copy(operations = ScriptedProvider(it.operations, scripts)) }

    override fun ofRevision(
        account: String,
        id: String,
        revision: String,
    ): ProviderOperations? = configured.ofRevision(account, id, revision)?.let { ScriptedProvider(it, scripts) }
}

private class ScriptedProvider(
    private val real: ProviderOperations,
    private val scripts: PaymentScripts,
) : ProviderOperations by real {
    override fun create(request: ProviderPaymentRequest): ProviderPayment = scripts.create(real, request) ?: real.create(request)

    override fun lookup(orderId: String): ProviderReport = scripts.lookup(id, orderId) ?: real.lookup(orderId)

    override fun verifiedCallback(request: CallbackRequest): VerifiedCallback? = scripts.notified(id, request)?.let(VerifiedCallback::Reported) ?: real.verifiedCallback(request)

    override fun unsignedCallback(request: CallbackRequest): ProviderReport? = scripts.notified(id, request) ?: real.unsignedCallback(request)
}
