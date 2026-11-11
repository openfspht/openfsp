package org.openfsp.gateway.webhooks

import org.openfsp.gateway.webhook.api.DeliverWebhooks
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutorService

@Component
class WebhookDispatchJob(
    private val webhooks: DeliverWebhooks,
    private val workers: ExecutorService,
) {
    @Scheduled(fixedDelayString = "\${openfsp.webhooks.poll-interval:500ms}")
    fun run() = webhooks.claimDue(BATCH).forEach { delivery -> workers.submit { webhooks.deliver(delivery) } }

    private companion object {
        const val BATCH = 20
    }
}
