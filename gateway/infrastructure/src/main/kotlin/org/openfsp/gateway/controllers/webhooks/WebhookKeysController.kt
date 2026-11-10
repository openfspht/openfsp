package org.openfsp.gateway.controllers.webhooks

import org.openfsp.gateway.persistence.webhook.JpaSigningKeys
import org.openfsp.gateway.webhook.PublishedSigningKey
import org.openfsp.gateway.webhook.api.RotateSigningKeys
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.Base64

data class JwkResource(
    val kid: String,
    val kty: String = "OKP",
    val crv: String = "Ed25519",
    val alg: String = "EdDSA",
    val use: String = "sig",
    val x: String,
    val openfspStatus: String,
)

data class JwkSetResource(
    val keys: List<JwkResource>,
)

@Component
class JwkMapper(
    private val keys: JpaSigningKeys,
) {
    fun toResource(key: PublishedSigningKey): JwkResource {
        val encoded = keys.publicKey(key.kid).encoded
        val raw = encoded.copyOfRange(encoded.size - RAW_KEY_BYTES, encoded.size)
        return JwkResource(kid = key.kid, x = Base64.getUrlEncoder().withoutPadding().encodeToString(raw), openfspStatus = key.status.wire)
    }

    private companion object {
        const val RAW_KEY_BYTES = 32
    }
}

@RestController
class WebhookKeysController(
    private val rotation: RotateSigningKeys,
    private val mapper: JwkMapper,
) {
    @GetMapping("/v1/webhooks/keys")
    fun keys(): JwkSetResource {
        rotation.current()
        return JwkSetResource(rotation.published().map(mapper::toResource))
    }
}
