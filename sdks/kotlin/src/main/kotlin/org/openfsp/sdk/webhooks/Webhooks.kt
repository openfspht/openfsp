package org.openfsp.sdk.webhooks

import org.openfsp.protocol.webhook.InMemoryNonceStore
import org.openfsp.protocol.webhook.NonceStore
import org.openfsp.protocol.webhook.Verification
import org.openfsp.protocol.webhook.WebhookHeaders
import org.openfsp.protocol.webhook.WebhookJwk
import org.openfsp.protocol.webhook.WebhookVerifier
import org.openfsp.sdk.InvalidSignatureException
import org.openfsp.sdk.http.Clock
import org.openfsp.sdk.http.asJavaClock
import org.openfsp.sdk.model.PaymentEvent
import org.openfsp.sdk.protocol.Json
import org.openfsp.sdk.protocol.Mapping
import java.util.TreeMap
import org.openfsp.protocol.api.PaymentEvent as WireEvent

class WebhookDelivery(
    val method: String,
    val url: String,
    headers: Map<String, String>,
    /** The body exactly as received: a parsed and re-serialized body no longer matches its digest. */
    val body: ByteArray,
) {
    val headers: Map<String, String> = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER).apply { putAll(headers) }

    override fun toString(): String = "WebhookDelivery($method $url)"
}

/** Verifies before parsing (webhooks#5.9). A verified event can still be a redelivery: deduplicate by event id. */
class Webhooks internal constructor(
    source: () -> List<WebhookJwk>,
    private val url: String?,
    nonceStore: NonceStore?,
    clock: Clock,
) {
    private val verifier =
        clock.asJavaClock().let { time -> WebhookVerifier(WebhookKeys(source, time), nonceStore ?: InMemoryNonceStore(time), time) }

    fun verify(delivery: WebhookDelivery): PaymentEvent {
        val verification = verifier.verify(delivery.method, url ?: delivery.url, { delivery.headers[it] }, delivery.body)
        if (verification is Verification.Rejected) throw InvalidSignatureException(verification.reason)
        val event = Json.read(delivery.body, WireEvent::class.java) ?: throw InvalidSignatureException("the body is not an event")
        if (event.id != delivery.headers[WebhookHeaders.EVENT_ID]) throw InvalidSignatureException("event id does not match ${WebhookHeaders.EVENT_ID}")
        if (event.type != delivery.headers[WebhookHeaders.EVENT_TYPE]) throw InvalidSignatureException("event type does not match ${WebhookHeaders.EVENT_TYPE}")
        return Mapping.toEvent(event)
    }

    companion object {
        /** Shares [nonceStore] across instances so a replay is caught whichever instance receives it. */
        @JvmStatic
        @JvmOverloads
        fun withKeys(
            keys: List<WebhookJwk>,
            url: String? = null,
            nonceStore: NonceStore? = null,
            clock: Clock = Clock.SYSTEM,
        ): Webhooks {
            val fixed = keys.toList()
            return Webhooks({ fixed }, url, nonceStore, clock)
        }
    }
}
