package org.openfsp.validator.shared.configuration

import org.openfsp.ddd.DomainService
import org.openfsp.validator.attestation.AttestationSettings
import org.openfsp.validator.attestation.spi.AttestationFormat
import org.openfsp.validator.attestation.spi.AttestationSignatures
import org.openfsp.validator.attestations.signing.JsonAttestationFormat
import org.openfsp.validator.engine.spi.SchemaCheckers
import org.openfsp.validator.engine.spi.SimulatorControls
import org.openfsp.validator.json.spi.JsonCodec
import org.openfsp.validator.report.ReportSettings
import org.openfsp.validator.report.spi.ReportFormat
import org.openfsp.validator.run.RunSettings
import org.openfsp.validator.run.SimulatedProvider
import org.openfsp.validator.run.SimulatorSettings
import org.openfsp.validator.attestations.signing.CompactJws
import org.openfsp.validator.runs.http.HostLookup
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
    fun hostLookup(): HostLookup = HostLookup { host -> InetAddress.getAllByName(host).toList() }

    @Bean
    fun simulatorControls(): SimulatorControls = EngineAdapters.simulators

    @Bean
    fun schemaCheckers(): SchemaCheckers = EngineAdapters.schemas

    @Bean
    fun jsonCodec(): JsonCodec = EngineAdapters.json

    @Bean
    fun reportFormat(): ReportFormat = EngineAdapters.reports

    @Bean
    fun attestationFormat(): AttestationFormat = JsonAttestationFormat

    @Bean
    fun attestationSignatures(): AttestationSignatures = CompactJws

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
            webhookCapacity = properties.webhookCapacity,
            simulator =
                SimulatorSettings(
                    publicUrl = properties.simulator.publicUrl,
                    environment = properties.simulator.environment,
                    keyPrefix = properties.simulator.keyPrefix,
                    providers = properties.simulator.providers.mapValues { (_, provider) -> SimulatedProvider(provider.basePath, provider.inputs) },
                ),
        )

    @Bean
    fun attestationSettings(properties: ValidatorProperties) = AttestationSettings(properties.attestation.publicUrl)

    @Bean
    fun reportSettings(properties: ValidatorProperties) = ReportSettings(properties.retention)
}
