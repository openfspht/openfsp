package org.openfsp.validator.report.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.validator.report.StoredReport
import org.openfsp.validator.report.spi.ReportStore
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Stub
class InMemoryReportStore : ReportStore {
    val reports = ConcurrentHashMap<UUID, StoredReport>()

    override fun save(report: StoredReport) {
        reports[report.id] = report
    }

    override fun find(id: UUID): StoredReport? = reports[id]

    override fun purgeCreatedBefore(before: Instant): Int {
        val expired = reports.values.filter { it.createdAt.isBefore(before) }
        expired.forEach { reports.remove(it.id) }
        return expired.size
    }
}
