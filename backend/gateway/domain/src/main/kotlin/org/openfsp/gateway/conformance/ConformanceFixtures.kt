package org.openfsp.gateway.conformance

import java.net.URI
import java.time.Duration
import java.time.Instant

data class ConformanceFixtures(
    val apiKey: String,
    val expiresAt: Instant,
    val inputs: Map<String, String>,
    val controlEndpoint: URI,
)

data class ConformanceSettings(
    val lifetime: Duration,
    val maxActive: Int,
    val controlEndpoint: URI,
    val settleAfter: Duration = Duration.ofMillis(500),
    val lateCallbackAfter: Duration = Duration.ofMinutes(2),
)

data class ConformanceRun(
    val account: String,
    val controller: String,
    val expiresAt: Instant,
) {
    fun isActiveAt(instant: Instant): Boolean = instant.isBefore(expiresAt)
}

data class ControlledPayment(
    val paymentId: String,
    val settled: Boolean,
    val callbacksSent: Int,
)
