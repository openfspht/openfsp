package org.openfsp.validator.engine

import org.openfsp.protocol.webhook.NonceStore
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class DeliveryNonces {
    private val seen = ConcurrentHashMap<String, String>()

    fun forDelivery(delivery: String): NonceStore =
        object : NonceStore {
            override fun reserve(
                nonce: String,
                until: Instant,
            ): Boolean = seen.putIfAbsent(nonce, delivery).let { it == null || it == delivery }

            override fun release(nonce: String) = Unit
        }
}
