package org.openfsp.simulator.configuration

import org.openfsp.ddd.DomainService
import org.openfsp.ddd.Stub
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.SimulationSettings
import org.openfsp.simulator.operator.spi.OperatorProfiles
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.web.client.RestClient
import java.time.Clock
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = ["org.openfsp.simulator.operator"],
    useDefaultFilters = false,
    includeFilters = [ComponentScan.Filter(type = FilterType.ANNOTATION, classes = [DomainService::class, Stub::class])],
)
class SimulatorConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean(destroyMethod = "shutdownNow")
    fun scheduler(): ScheduledExecutorService = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory())

    @Bean
    fun callbackClient(builder: RestClient.Builder): RestClient = builder.build()

    @Bean
    fun simulationSettings(properties: SimulatorProperties) =
        SimulationSettings(properties.responseDelay, properties.settleAfter, properties.hangDuration, properties.lateCallbackDelay, properties.publicBaseUrl)

    @Bean
    fun operatorProfiles(properties: SimulatorProperties): OperatorProfiles {
        val profiles =
            properties.operators.mapValues { (id, operator) ->
                OperatorProfile(
                    id = id,
                    dialect = operator.dialect,
                    referencePrefix = operator.referencePrefix,
                    lookup = operator.lookup,
                    callbacks = operator.callbacks,
                    idempotentByOrder = operator.idempotentByOrder,
                    statements = operator.statements,
                    returnUrl = operator.returnUrl,
                )
            }
        return OperatorProfiles { profiles[it] }
    }
}
