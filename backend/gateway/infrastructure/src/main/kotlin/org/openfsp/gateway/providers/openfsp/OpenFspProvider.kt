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
import org.openfsp.protocol.webhook.WebhookHeaders
import org.openfsp.sdk.OpenFsp
import org.openfsp.sdk.OpenFspException
import org.openfsp.sdk.webhooks.WebhookDelivery
import java.time.LocalDate

class OpenFspProvider(
    override val id: String,
    override val limits: AmountLimits,
    private val server: OpenFsp,
    private val mapper: OpenFspMapper,
    private val capabilities: OpenFspCapabilities,
) : ProviderOperations {
    override val descriptor: ProviderDescriptor get() = capabilities.descriptor()

    override fun create(request: ProviderPaymentRequest): ProviderPayment =
        asking { mapper.toPayment(server.payments.create(mapper.toPaymentRequest(request, descriptor), mapper.idempotencyKeyOf(request.orderId))) }

    override fun lookup(orderId: String): ProviderReport =
        asking { mapper.toReport(server.payments.findByReference(orderId) ?: throw ProviderFailure.Rejected("The provider knows no payment for this order")) }

    override fun verifiedCallback(request: CallbackRequest): ProviderReport? {
        if (!descriptor.signedCallbacks) return null
        val target = request.targetUri ?: return null
        val headers = SIGNED_HEADERS.mapNotNull { name -> request.headers(name)?.let { name to it } }.toMap()
        return try {
            mapper.toReport(server.webhooks.verify(WebhookDelivery(request.method, target.toString(), headers, request.body)).payment)
        } catch (_: OpenFspException) {
            null
        } catch (_: ProviderFailure) {
            null
        }
    }

    override fun unsignedCallback(request: CallbackRequest): ProviderReport? = null

    override fun statement(day: LocalDate): ProviderStatement = asking { mapper.toStatement(day, server.statements.find(id, day)) }

    private fun <T> asking(call: () -> T): T =
        try {
            call()
        } catch (failure: OpenFspException) {
            throw mapper.toFailure(failure)
        }

    private companion object {
        val SIGNED_HEADERS =
            listOf(
                WebhookHeaders.CONTENT_TYPE,
                WebhookHeaders.CONTENT_DIGEST,
                WebhookHeaders.EVENT_ID,
                WebhookHeaders.EVENT_TYPE,
                WebhookHeaders.DELIVERY_ATTEMPT,
                WebhookHeaders.SIGNATURE_INPUT,
                WebhookHeaders.SIGNATURE,
            )
    }
}
