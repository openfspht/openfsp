package org.openfsp.validator.runs.http

import org.openfsp.validator.shared.json.JacksonJsonCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalWebhookReceiverTest {
    @Test
    fun `a receiver listening on every address announces the loopback address, never the wildcard`() {
        LocalWebhookReceiver("0.0.0.0", 0, null, JacksonJsonCodec(), WebhookBodies()).use {
            assertTrue(it.inbox.url.startsWith("http://127.0.0.1:"), it.inbox.url)
        }
    }

    @Test
    fun `a given public URL is announced as is`() {
        LocalWebhookReceiver("0.0.0.0", 0, "https://receiver.example/webhooks", JacksonJsonCodec(), WebhookBodies()).use {
            assertEquals("https://receiver.example/webhooks", it.inbox.url)
        }
    }
}
