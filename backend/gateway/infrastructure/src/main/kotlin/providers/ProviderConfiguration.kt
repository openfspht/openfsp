package org.openfsp.gateway.providers

import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.conformance.ConformanceProperties
import org.openfsp.gateway.shared.configuration.DemoMode
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.protocol.Money
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import java.net.URI

@Configuration(proxyBeanMethods = false)
class ProviderConfiguration(
    private val properties: ProviderProperties,
    gateway: GatewayProperties,
    demo: DemoMode,
    conformance: ConformanceProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        check(simulated().isEmpty() || gateway.environment == GatewayProperties.Environment.TEST) {
            "a gateway configured against the simulator runs in the test environment"
        }
        check(!demo.active || properties.providers.values.all { it.simulated }) {
            "the demo publishes its admin token and API key, so it serves simulated providers only: ${properties.providers.filterValues { !it.simulated }.keys.sorted()} are not"
        }
        properties.providers.forEach { (id, settings) ->
            listOfNotNull(settings.baseUrl, settings.secondaryUrl).forEach { url ->
                check(url.scheme.equals("https", ignoreCase = true) || settings.simulated || gateway.developmentMode) {
                    "provider $id is reached over https only: $url"
                }
            }
        }
        check(!conformance.enabled || properties.providers.values.all { it.simulated }) {
            "conformance preparation issues keys to anonymous callers, so it serves simulated providers only: ${properties.providers.filterValues { !it.simulated }.keys.sorted()} are not"
        }
    }

    @EventListener(ApplicationReadyEvent::class)
    fun announce() {
        if (simulated().isNotEmpty()) log.warn("Configured against the simulator: {}", simulated().sorted())
    }

    private fun simulated(): List<String> = properties.providers.filterValues { it.simulated }.keys.toList()

    @Bean
    fun providers(
        properties: ProviderProperties,
        gateway: GatewayProperties,
        factories: List<ProviderFactory>,
        credentials: ProviderCredentialStore,
    ): ConfiguredProviders {
        val byType = factories.associateBy { it.type }
        properties.providers.forEach { (id, settings) ->
            val unset = settings.unsetSecrets()
            check(unset.isEmpty()) { "provider $id has no value for ${unset.joinToString()}: set them in the environment or in OPENFSP_SECRETS_DIR" }
        }
        return ConfiguredProviders(
            properties.providers.map { (id, settings) ->
                val factory = byType[settings.type] ?: error("No adapter of type ${settings.type} for provider $id")
                val setup = ProviderSetup(id, settings, settings.limits.toDomain(), settings.logoUrl?.let { logoOf(gateway.publicBaseUrl, it) })
                val platform = if (factory.accountCredentials.isNotEmpty() && settings.credentials.isEmpty()) null else factory.create(setup)
                ConfiguredProvider(setup, factory, platform)
            },
            credentials,
        )
    }

    private fun logoOf(
        publicBaseUrl: URI,
        logo: String,
    ): URI = URI(logo).takeIf { it.isAbsolute } ?: publicBaseUrl.resolve(logo)

    private fun ProviderProperties.Limits.toDomain() = AmountLimits(minAmount?.let(Money::htg), maxAmount?.let(Money::htg), amountStep?.let(Money::htg))
}
