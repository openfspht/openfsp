package org.openfsp.sdk.testing

import org.openfsp.protocol.api.PaymentEvent
import org.openfsp.protocol.webhook.KeyStatus
import org.openfsp.protocol.webhook.WebhookJwk
import org.openfsp.sdk.webhooks.WebhookDelivery
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.openfsp.protocol.webhook.WebhookSigner as ProtocolSigner

class WebhookSigner private constructor(
    private val keys: KeyPair,
    val keyId: String,
) {
    val jwk: WebhookJwk = WebhookJwk.of(keyId, keys.public, KeyStatus.ACTIVE)

    @JvmOverloads
    fun sign(
        url: String,
        event: PaymentEvent = Wire.event(),
        at: Instant = Instant.now(),
    ): WebhookDelivery {
        val body = Responses.mapper.writeValueAsBytes(event)
        val headers = ProtocolSigner(keys.private, keyId, Clock.fixed(at, ZoneOffset.UTC)).sign(url, body, event.id, event.type)
        return WebhookDelivery("POST", url, headers, body)
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun generate(keyId: String = "key_test"): WebhookSigner = WebhookSigner(KeyPairGenerator.getInstance("Ed25519").generateKeyPair(), keyId)
    }
}
