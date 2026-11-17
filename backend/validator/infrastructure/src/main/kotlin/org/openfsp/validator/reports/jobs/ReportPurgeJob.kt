package org.openfsp.validator.reports.jobs

import org.openfsp.validator.report.api.PurgeReports
import org.openfsp.validator.shared.configuration.ValidatorProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ReportPurgeJob(
    private val reports: PurgeReports,
    private val properties: ValidatorProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${openfsp.validator.purge-interval:6h}", initialDelayString = "\${openfsp.validator.purge-delay:1m}")
    fun run() {
        val purged = reports.purgeExpired()
        if (purged > 0) log.info("purged {} reports older than {}", purged, properties.retention)
    }
}
