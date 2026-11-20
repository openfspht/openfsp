package org.openfsp.gateway.webhooks

import org.openfsp.gateway.webhooks.persistence.JpaNonceStore
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class WebhookNoncePurgeJob(
    private val nonces: JpaNonceStore,
) {
    @Scheduled(fixedDelay = 5, initialDelay = 5, timeUnit = TimeUnit.MINUTES)
    fun run() {
        nonces.purgeExpired()
    }
}
