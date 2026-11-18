package org.openfsp.validator.reports.persistence

import org.openfsp.validator.report.StoredReport
import org.openfsp.validator.report.spi.ReportFormat
import org.openfsp.validator.report.spi.ReportStore
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Component
class JpaReportStore(
    private val repository: ReportRepository,
    private val format: ReportFormat,
    private val mapper: ReportMapper,
) : ReportStore {
    @Transactional
    override fun save(report: StoredReport) {
        repository.save(mapper.toEntity(report, format.digest(report.body) ?: throw IllegalArgumentException("report ${report.id} is not a JSON document")))
    }

    @Transactional(readOnly = true)
    override fun find(id: UUID): StoredReport? = repository.findByIdOrNull(id)?.let(mapper::toDomain)

    @Transactional
    override fun purgeCreatedBefore(before: Instant): Int = repository.deleteCreatedBefore(before)
}

@Component
class ReportMapper {
    fun toEntity(
        report: StoredReport,
        digest: String,
    ) =
        ReportEntity(
            id = report.id,
            createdAt = report.createdAt,
            target = report.target,
            baseUrl = report.baseUrl,
            core = report.core,
            suiteVersion = report.suiteVersion,
            digest = digest,
            body = report.body,
        )

    fun toDomain(entity: ReportEntity) =
        StoredReport(
            id = entity.id,
            createdAt = entity.createdAt,
            target = entity.target,
            baseUrl = entity.baseUrl,
            core = entity.core,
            suiteVersion = entity.suiteVersion,
            body = entity.body,
        )
}
