package org.openfsp.gateway.discovery

import org.openfsp.gateway.webhook.PublishedSigningKey
import org.openfsp.protocol.webhook.Ed25519Keys
import org.openfsp.protocol.webhook.WebhookJwk
import org.springframework.stereotype.Component

@Component
class JwkMapper {
    fun toResource(key: PublishedSigningKey): WebhookJwk = WebhookJwk(kid = key.kid, x = Ed25519Keys.toRaw(key.publicKey), openfspStatus = key.status.wire)
}
