package org.openfsp.gateway.webhooks

import org.openfsp.gateway.webhooks.persistence.JpaNonceStore
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class WebhookNoncePurgeJob(
    private val nonces: JpaNonceStore,
) {
    @Scheduled(fixedDelayString = "\${openfsp.webhooks.nonce-purge-interval:5m}", initialDelayString = "\${openfsp.webhooks.nonce-purge-interval:5m}")
    fun run() {
        nonces.purgeExpired()
    }
}
