package org.openfsp.gateway.webhook

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class WebhookKeysController(
    private val keys: SigningKeys,
) {
    @GetMapping("/v1/webhooks/keys")
    fun keys(): JwkSet {
        keys.signer()
        return keys.published()
    }
}
