package org.openfsp.simulator.dialects.openfsp

import org.openfsp.protocol.ResourceIds
import org.openfsp.protocol.webhook.KeyStatus
import org.openfsp.protocol.webhook.WebhookJwk
import org.openfsp.protocol.webhook.WebhookSigner
import org.springframework.stereotype.Component
import java.security.KeyPairGenerator
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

@Component
class OpenFspWebhookKeys(
    private val clock: Clock,
) {
    private class SigningKey(
        val jwk: WebhookJwk,
        val signer: WebhookSigner,
    )

    private val keys = ConcurrentHashMap<String, SigningKey>()

    fun published(operator: String): List<WebhookJwk> = listOf(keyOf(operator).jwk)

    fun signer(operator: String): WebhookSigner = keyOf(operator).signer

    private fun keyOf(operator: String): SigningKey =
        keys.computeIfAbsent(operator) {
            val pair = KeyPairGenerator.getInstance(ED25519).generateKeyPair()
            val kid = ResourceIds.generate("whk")
            SigningKey(WebhookJwk.of(kid, pair.public, KeyStatus.ACTIVE), WebhookSigner(pair.private, kid, clock))
        }

    private companion object {
        const val ED25519 = "Ed25519"
    }
}
