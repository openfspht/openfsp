package org.openfsp.gateway.providers.openfsp

import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.CallbackRequest
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.provider.ProviderPayment
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.ProviderStatement
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.providers.ProviderJson
import org.openfsp.protocol.api.PaymentEvent
import org.openfsp.protocol.webhook.Verification
import org.openfsp.protocol.webhook.WebhookVerifier
import java.time.LocalDate

class OpenFspProvider(
    override val id: String,
    override val limits: AmountLimits,
    private val api: OpenFspApi,
    private val mapper: OpenFspMapper,
    private val capabilities: OpenFspCapabilities,
    private val verifier: WebhookVerifier,
    private val json: ProviderJson,
) : ProviderOperations {
    override val descriptor: ProviderDescriptor get() = capabilities.descriptor()

    override fun create(request: ProviderPaymentRequest): ProviderPayment =
        mapper.toPayment(api.create(mapper.toPaymentRequest(request, descriptor), mapper.idempotencyKeyOf(request.orderId)))

    override fun lookup(orderId: String): ProviderReport = mapper.toReport(api.byReference(orderId) ?: throw ProviderFailure.Rejected("The provider knows no payment for this order"))

    override fun verifiedCallback(request: CallbackRequest): ProviderReport? {
        if (!descriptor.hasSignedCallbacks) return null
        val target = request.targetUri ?: return null
        val verification = verifier.verify(request.method, target.toString(), request.headers, request.body)
        return if (verification is Verification.Verified) eventOf(request) else null
    }

    override fun unsignedCallback(request: CallbackRequest): ProviderReport? = if (descriptor.hasPaymentUrl) eventOf(request) else null

    override fun statement(day: LocalDate): ProviderStatement = mapper.toStatement(day, api.statement(id, day))

    private fun eventOf(request: CallbackRequest): ProviderReport? {
        val event = json.readOrNull(request.body, PaymentEvent::class.java)?.takeIf { it.resourceType == PaymentEvent.PAYMENT } ?: return null
        return runCatching { mapper.toReport(event.data) }.getOrNull()
    }
}
