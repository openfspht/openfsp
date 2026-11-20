package org.openfsp.validator.shared.configuration

import org.openfsp.ddd.DomainService
import org.openfsp.validator.attestation.AttestationSettings
import org.openfsp.validator.engine.spi.Pause
import org.openfsp.validator.report.ReportSettings
import org.openfsp.validator.run.RunSettings
import org.openfsp.validator.runs.http.HostLookup
import org.openfsp.validator.runs.web.RunsController
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import java.net.InetAddress
import java.time.Clock
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = ["org.openfsp.validator"],
    useDefaultFilters = false,
    includeFilters = [ComponentScan.Filter(type = FilterType.ANNOTATION, classes = [DomainService::class])],
)
class ValidatorConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean(destroyMethod = "shutdownNow")
    fun runExecutor(): ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    @Bean
    fun pause(): Pause = Pause(Thread::sleep)

    @Bean
    fun hostLookup(): HostLookup = HostLookup { host -> InetAddress.getAllByName(host).toList() }

    @Bean
    fun runSettings(properties: ValidatorProperties) =
        RunSettings(
            requestTimeout = properties.requestTimeout,
            finishedRunMemory = properties.finishedRunMemory,
            runsPerAddress = properties.runsPerAddress,
            runsWindow = properties.runsWindow,
            concurrency = properties.concurrency,
            preparedRunLifetime = properties.preparedRunLifetime,
            publicUrl = properties.publicUrl,
            runsPath = RunsController.BASE,
            webhookCapacity = properties.webhookCapacity,
            webhookBytes = properties.webhookBytes,
            maxPreparedRuns = properties.maxPreparedRuns,
        )

    @Bean
    fun attestationSettings(properties: ValidatorProperties) = AttestationSettings(properties.publicUrl)

    @Bean
    fun reportSettings(properties: ValidatorProperties) = ReportSettings(properties.retention)
}
