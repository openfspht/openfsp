package org.openfsp.simulator.emission

import java.time.Duration

class EncodedEvent(
    val id: String,
    val type: String,
    val body: ByteArray,
)

data class DeliveryResult(
    val status: Int?,
    val retryAfter: Duration? = null,
) {
    val succeeded: Boolean get() = status in SUCCESS

    val gone: Boolean get() = status == GONE

    private companion object {
        val SUCCESS = 200..299
        const val GONE = 410
    }
}
