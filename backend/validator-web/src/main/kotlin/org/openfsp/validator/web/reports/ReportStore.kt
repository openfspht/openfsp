package org.openfsp.validator.web.reports

import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
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
    val digest: String get() = HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST).digest(body.toByteArray(Charsets.UTF_8)))

    private companion object {
        const val DIGEST = "SHA-256"
    }
}

interface ReportStore {
    fun save(report: StoredReport)

    fun find(id: UUID): StoredReport?

    fun purgeCreatedBefore(before: Instant): Int
}
