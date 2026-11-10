package org.openfsp.gateway.providers.simulated

import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.CallbackRequest
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.ProviderPayment
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.ProviderStatement
import org.openfsp.gateway.provider.spi.ProviderOperations
import java.time.LocalDate

class SimulatedProvider(
    override val id: String,
    override val descriptor: ProviderDescriptor,
    override val limits: AmountLimits,
    private val api: SimulatedApi,
    private val mapper: SimulatedMapper,
    private val callbacks: SimulatedCallbacks,
) : ProviderOperations {
    override fun create(request: ProviderPaymentRequest): ProviderPayment = mapper.toPayment(api.createPayment(mapper.toPaymentRequest(request)))

    override fun lookup(orderId: String): ProviderReport = mapper.toReport(api.paymentOf(orderId))

    override fun verifiedCallback(request: CallbackRequest): ProviderReport? =
        callbacks.signed(request)?.takeIf { descriptor.hasSignedCallbacks }?.let(mapper::toReport)

    override fun unsignedCallback(request: CallbackRequest): ProviderReport? =
        callbacks.unsigned(request)?.takeIf { descriptor.hasPaymentUrl }?.let(mapper::toReport)

    override fun statement(day: LocalDate): ProviderStatement = mapper.toStatement(api.statement(day))
}
