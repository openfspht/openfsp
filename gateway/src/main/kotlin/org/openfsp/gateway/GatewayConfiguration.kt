package org.openfsp.gateway

import org.openfsp.protocol.OrderIdDerivation
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.web.filter.UrlHandlerFilter
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class GatewayConfiguration(
    private val properties: GatewayProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        val simulated = properties.providers.keys.filter { it.startsWith(SIMULATED_PREFIX) }
        check(simulated.isEmpty() || properties.environment == GatewayProperties.Environment.TEST) {
            "a gateway configured against the simulator runs in the test environment"
        }
        check(properties.environment == GatewayProperties.Environment.TEST || !properties.developmentMode) {
            "development mode is only available in the test environment"
        }
    }

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun orderIdDerivation(): OrderIdDerivation = OrderIdDerivation(properties.derivationKeyBytes)

    @Bean
    fun trailingSlashFilter(): UrlHandlerFilter = UrlHandlerFilter.trailingSlashHandler("/**").wrapRequest().build()

    @EventListener(ApplicationReadyEvent::class)
    fun announce() {
        if (properties.developmentMode) log.warn("Development mode: the API is served over plain HTTP")
        val simulated = properties.providers.keys.filter { it.startsWith(SIMULATED_PREFIX) }
        if (simulated.isNotEmpty()) log.warn("Configured against the simulator: {}", simulated.sorted())
    }

    private companion object {
        const val SIMULATED_PREFIX = "mock_"
    }
}
