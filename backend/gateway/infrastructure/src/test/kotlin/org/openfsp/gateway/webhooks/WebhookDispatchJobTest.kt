package org.openfsp.gateway.webhooks

import org.openfsp.gateway.webhook.Delivery
import org.openfsp.gateway.webhook.api.DeliverWebhooks
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebhookDispatchJobTest {
    private val workers = Executors.newVirtualThreadPerTaskExecutor()

    @AfterTest
    fun stop() {
        workers.shutdownNow()
    }

    private class Webhooks(
        private val release: CountDownLatch,
        private val failing: Boolean = false,
    ) : DeliverWebhooks {
        val limits = mutableListOf<Int>()
        val delivered = CountDownLatch(1)
        private var next = 0L

        override fun claimDue(limit: Int): List<Delivery> {
            limits += limit
            return (1..limit).map { Delivery(++next, "evt_$next", "payment.created", "shop", URI("https://shop.example/hooks"), 0, ByteArray(0)) }
        }

        override fun deliver(delivery: Delivery) {
            release.await()
            delivered.countDown()
            if (failing) error("delivery failed")
        }
    }

    @Test
    fun `deliveries in flight never exceed the connection pool, so a claimed delivery never outlives its lease`() {
        val release = CountDownLatch(1)
        val webhooks = Webhooks(release)
        val job = WebhookDispatchJob(webhooks, workers)

        repeat(WebhookDispatchJob.MAX_IN_FLIGHT / WebhookDispatchJob.BATCH + 2) { job.run() }

        assertEquals(WebhookDispatchJob.MAX_IN_FLIGHT, webhooks.limits.sum())
        release.countDown()
    }

    @Test
    fun `a failing delivery frees its place`() {
        val release = CountDownLatch(0)
        val webhooks = Webhooks(release, failing = true)
        val job = WebhookDispatchJob(webhooks, workers)

        repeat(WebhookDispatchJob.MAX_IN_FLIGHT / WebhookDispatchJob.BATCH) { job.run() }
        assertTrue(webhooks.delivered.await(5, TimeUnit.SECONDS))
        workers.shutdown()
        assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS))

        assertEquals(WebhookDispatchJob.MAX_IN_FLIGHT, job.free)
    }
}
