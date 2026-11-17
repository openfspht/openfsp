package org.openfsp.gateway.conformance

import java.time.Duration
import java.time.Instant

data class ConformanceFixtures(
    val apiKey: String,
    val expiresAt: Instant,
    val inputs: Map<String, String>,
)

data class ConformanceSettings(
    val lifetime: Duration,
    val maxActive: Int,
)
