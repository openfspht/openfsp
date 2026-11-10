package org.openfsp.gateway.configuration

import org.openfsp.gateway.providers.ProviderProperties
import org.openfsp.gateway.webhooks.WebhookProperties
import org.openfsp.protocol.OrderIdDerivation
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.filter.UrlHandlerFilter
import java.time.Clock

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class GatewayConfiguration(
    private val properties: GatewayProperties,
    private val providers: ProviderProperties,
    webhooks: WebhookProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        check(simulated().isEmpty() || properties.environment == GatewayProperties.Environment.TEST) {
            "a gateway configured against the simulator runs in the test environment"
        }
        check(properties.environment == GatewayProperties.Environment.TEST || !properties.developmentMode) {
            "development mode is only available in the test environment"
        }
        check(properties.bootstrapKey == null || properties.developmentMode) { "a bootstrap API key is only accepted in development mode" }
        webhooks.endpoints.forEach { (name, endpoint) ->
            check(endpoint.url.scheme == "https" || properties.developmentMode) { "webhook endpoint $name must use https" }
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
        if (simulated().isNotEmpty()) log.warn("Configured against the simulator: {}", simulated().sorted())
    }

    private fun simulated(): List<String> = providers.providers.keys.filter { it.startsWith(SIMULATED_PREFIX) }

    private companion object {
        const val SIMULATED_PREFIX = "mock_"
    }
}
