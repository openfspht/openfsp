package org.openfsp.validator.engine

import org.openfsp.protocol.webhook.WebhookJwk
import java.security.PublicKey

internal object PublishedKeys {
    fun of(
        kty: String?,
        crv: String?,
        alg: String?,
        x: String?,
        y: String?,
    ): PublicKey? {
        if (alg == null) return null
        return WebhookJwk.publicKeyOf(kty, crv, alg, x, y.takeIf { kty == WebhookJwk.EC })
    }
}
