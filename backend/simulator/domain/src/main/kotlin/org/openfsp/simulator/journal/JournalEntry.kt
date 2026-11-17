package org.openfsp.simulator.journal

import java.time.Instant

data class JournalEntry(
    val operator: String,
    val receivedAt: Instant,
    val method: String,
    val path: String,
    val body: String,
    val remoteAddress: String = "",
    val headers: Map<String, String> = emptyMap(),
    val status: Int = 0,
    val durationMs: Long = 0,
    val responseHeaders: Map<String, String> = emptyMap(),
    val responseBody: String = "",
)
