package org.openfsp.validator.web.reports

import org.openfsp.validator.report.Reports
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

interface ReportStore {
    fun save(report: StoredReport)

    fun find(id: UUID): StoredReport?

    fun purgeCreatedBefore(before: Instant): Int
}
