package org.openfsp.validator.reports

import org.openfsp.validator.report.api.PurgeReports
import org.openfsp.validator.shared.configuration.ValidatorProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.FixedDelayTask
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.stereotype.Component

@Component
class ReportPurgeJob(
    private val reports: PurgeReports,
    private val properties: ValidatorProperties,
) : SchedulingConfigurer {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun configureTasks(registrar: ScheduledTaskRegistrar) = registrar.addFixedDelayTask(FixedDelayTask(::run, properties.purgeInterval, properties.purgeDelay))

    fun run() {
        val purged = reports.purgeExpired()
        if (purged > 0) log.info("purged {} reports older than {}", purged, properties.retention)
    }
}
