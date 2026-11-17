package org.openfsp.gateway.webhooks

import org.openfsp.gateway.webhook.api.DeliverWebhooks
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutorService
import java.util.concurrent.Semaphore

@Component
class WebhookDispatchJob(
    private val webhooks: DeliverWebhooks,
    private val workers: ExecutorService,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val places = Semaphore(MAX_IN_FLIGHT)

    internal val free: Int get() = places.availablePermits()

    @Scheduled(fixedDelayString = "\${openfsp.webhooks.poll-interval:500ms}")
    fun run() {
        val claimable = minOf(places.availablePermits(), BATCH)
        if (claimable == 0) return
        webhooks.claimDue(claimable).forEach { delivery ->
            places.acquire()
            workers.execute {
                try {
                    webhooks.deliver(delivery)
                } catch (exception: Exception) {
                    log.error("Delivery {} of event {} failed", delivery.id, delivery.eventId, exception)
                } finally {
                    places.release()
                }
            }
        }
    }

    internal companion object {
        const val BATCH = 20
        const val MAX_IN_FLIGHT = HttpWebhookTransport.MAX_CONNECTIONS
    }
}
