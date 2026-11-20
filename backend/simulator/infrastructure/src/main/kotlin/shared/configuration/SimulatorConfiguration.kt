package org.openfsp.simulator.shared.configuration

import org.openfsp.ddd.DomainService
import org.openfsp.http.GuardedHttpClients
import org.openfsp.http.HttpClientLimits
import org.openfsp.simulator.callbacks.CallbackAddressGuard
import org.openfsp.simulator.common.SimulationSettings
import org.openfsp.simulator.common.spi.PublicAddress
import org.openfsp.simulator.emission.RunTerms
import org.openfsp.simulator.memory.MemoryConformanceRuns
import org.openfsp.simulator.memory.MemoryIdempotencyRecords
import org.openfsp.simulator.memory.MemoryRequestJournal
import org.openfsp.simulator.memory.MemoryScenarioBook
import org.openfsp.simulator.memory.MemorySequences
import org.openfsp.simulator.memory.MemorySimulatedPayments
import org.openfsp.simulator.operator.AmountLimits
import org.openfsp.simulator.operator.OperatorFee
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.spi.OperatorProfiles
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
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
    fun callbackClient(properties: SimulatorProperties): RestClient {
        val callbacks = properties.callbacks
        val guard = CallbackAddressGuard(properties.httpCallbackHosts, callbacks.privateAddressesAllowed)
        val http =
            GuardedHttpClients.create(
                guard::resolve,
                HttpClientLimits(
                    connectTimeout = callbacks.connectTimeout,
                    socketTimeout = callbacks.readTimeout,
                    connectionRequestTimeout = callbacks.connectTimeout,
                    maxConnections = MAX_CALLBACK_CONNECTIONS,
                    maxConnectionsPerRoute = MAX_CALLBACK_CONNECTIONS_PER_ROUTE,
                ),
            )
        return RestClient.builder().requestFactory(HttpComponentsClientHttpRequestFactory(http)).build()
    }

    @Bean
    fun requestJournal(properties: SimulatorProperties) = MemoryRequestJournal(properties.journalCapacity)

    @Bean
    fun simulatedPayments(): MemorySimulatedPayments = MemorySimulatedPayments()

    @Bean
    fun sequences(): MemorySequences = MemorySequences()

    @Bean
    fun idempotencyRecords(): MemoryIdempotencyRecords = MemoryIdempotencyRecords()

    @Bean
    fun scenarioBook(): MemoryScenarioBook = MemoryScenarioBook()

    @Bean
    fun conformanceRuns(): MemoryConformanceRuns = MemoryConformanceRuns()

    @Bean
    fun simulationSettings(properties: SimulatorProperties) =
        SimulationSettings(properties.responseDelay, properties.settleAfter, properties.hangDuration, properties.lateCallbackDelay)

    @Bean
    fun runTerms(properties: SimulatorProperties) = RunTerms(properties.conformance.runLifetime, properties.conformance.maxActiveRuns)

    @Bean
    fun publicAddress(properties: SimulatorProperties) = PublicAddress { properties.publicBaseUrl.trimEnd('/') }

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
                    idempotentByReference = operator.idempotentByReference,
                    statements = operator.statements,
                    returnUrl = operator.returnUrl,
                    webhookUrl = operator.webhookUrl,
                    fee = operator.fee?.let { OperatorFee(it.rateBasisPoints, it.bearer) },
                    emitsEvents = operator.emitsEvents,
                    limits = operator.limits?.let { AmountLimits(it.minAmount, it.maxAmount, it.amountStep) },
                )
            }
        return OperatorProfiles { profiles[it] }
    }

    private companion object {
        const val MAX_CALLBACK_CONNECTIONS = 64
        const val MAX_CALLBACK_CONNECTIONS_PER_ROUTE = 8
    }
}
