package org.openfsp.validator.web.reports

import org.openfsp.validator.web.configuration.ValidatorProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class ReportPurgeJob(
    private val reports: ReportStore,
    private val properties: ValidatorProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${openfsp.validator.purge-interval:6h}", initialDelayString = "\${openfsp.validator.purge-delay:1m}")
    fun run() {
        val purged = reports.purgeCreatedBefore(clock.instant().minus(properties.retention))
        if (purged > 0) log.info("purged {} reports older than {}", purged, properties.retention)
    }
}
