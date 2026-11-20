package org.openfsp.simulator.emission

import java.time.Instant

data class ConformanceRun(
    val id: String,
    val operator: String,
    val credential: String,
    val restrictedCredential: String,
    val webhookUrl: String?,
    val expiresAt: Instant,
) {
    fun isActiveAt(now: Instant): Boolean = now.isBefore(expiresAt)
}
