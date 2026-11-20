package org.openfsp.validator.report

import org.openfsp.ddd.DomainService
import org.openfsp.validator.report.api.PurgeReports
import org.openfsp.validator.report.spi.ReportStore
import java.time.Clock
import java.time.Duration

data class ReportSettings(
    val retention: Duration,
)

@DomainService
class ReportRetention(
    private val reports: ReportStore,
    private val settings: ReportSettings,
    private val clock: Clock,
) : PurgeReports {
    override fun purgeExpired(): Int = reports.purgeCreatedBefore(clock.instant().minus(settings.retention))
}
