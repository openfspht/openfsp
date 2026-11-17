package org.openfsp.simulator.shared.configuration

import org.openfsp.ddd.DomainService
import org.openfsp.simulator.callbacks.CallbackClients
import org.openfsp.simulator.common.SimulationSettings
import org.openfsp.simulator.memory.MemoryRequestJournal
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.spi.OperatorProfiles
import org.openfsp.simulator.space.SpaceSettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.web.client.RestClient
import java.time.Clock

@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = ["org.openfsp.simulator"],
    useDefaultFilters = false,
    includeFilters = [ComponentScan.Filter(type = FilterType.ANNOTATION, classes = [DomainService::class])],
)
class SimulatorConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun callbackClient(properties: SimulatorProperties): RestClient = CallbackClients.create(properties.callbacks, properties.httpCallbackHosts)

    @Bean
    fun requestJournal(properties: SimulatorProperties) = MemoryRequestJournal(properties.journalCapacity)

    @Bean
    fun simulationSettings(properties: SimulatorProperties) =
        SimulationSettings(properties.responseDelay, properties.settleAfter, properties.hangDuration, properties.lateCallbackDelay)

    @Bean
    fun spaceSettings(properties: SimulatorProperties) = SpaceSettings(properties.spaces.idleLifetime, properties.spaces.capacity)

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
