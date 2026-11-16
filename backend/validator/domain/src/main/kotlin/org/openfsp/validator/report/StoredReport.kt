package org.openfsp.validator.report

import java.time.Instant
import java.util.UUID

data class StoredReport(
    val id: UUID,
    val createdAt: Instant,
    val target: String,
    val baseUrl: String,
    val core: String,
    val suiteVersion: String,
    val body: String,
) {
    val digest: String get() = digestOf(body)

    companion object {
        fun digestOf(body: String): String = CanonicalJson.sha256(Reports.json.readTree(body))
    }
}
