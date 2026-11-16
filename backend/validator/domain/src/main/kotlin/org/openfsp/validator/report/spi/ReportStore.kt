package org.openfsp.validator.report.spi

import org.openfsp.validator.report.StoredReport
import java.time.Instant
import java.util.UUID

interface ReportStore {
    fun save(report: StoredReport)

    fun find(id: UUID): StoredReport?

    fun purgeCreatedBefore(before: Instant): Int
}
