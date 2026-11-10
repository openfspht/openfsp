package org.openfsp.gateway.discovery

import org.openfsp.gateway.webhook.PublishedSigningKey
import org.openfsp.gateway.webhooks.persistence.JpaSigningKeys
import org.openfsp.protocol.webhook.Ed25519Keys
import org.openfsp.protocol.webhook.WebhookJwk
import org.springframework.stereotype.Component

@Component
class JwkMapper(
    private val keys: JpaSigningKeys,
) {
    fun toResource(key: PublishedSigningKey): WebhookJwk = WebhookJwk(kid = key.kid, x = Ed25519Keys.toRaw(keys.publicKey(key.kid)), openfspStatus = key.status.wire)
}
