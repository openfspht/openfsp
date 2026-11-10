package org.openfsp.gateway.controllers.webhooks

import org.openfsp.gateway.webhooks.PublishedKey
import org.openfsp.gateway.webhooks.SigningKeys
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
class JwkMapper {
    fun toResource(key: PublishedKey): JwkResource {
        val encoded = key.publicKey.encoded
        val raw = encoded.copyOfRange(encoded.size - RAW_KEY_BYTES, encoded.size)
        return JwkResource(kid = key.kid, x = Base64.getUrlEncoder().withoutPadding().encodeToString(raw), openfspStatus = key.status)
    }

    private companion object {
        const val RAW_KEY_BYTES = 32
    }
}

@RestController
class WebhookKeysController(
    private val keys: SigningKeys,
    private val mapper: JwkMapper,
) {
    @GetMapping("/v1/webhooks/keys")
    fun keys(): JwkSetResource = JwkSetResource(keys.published().map(mapper::toResource))
}
