package org.openfsp.gateway.configuration

import org.openfsp.gateway.providers.ProviderProperties
import org.openfsp.gateway.security.admin.AdminTokenHashing
import org.openfsp.gateway.webhook.EndpointAddresses
import org.openfsp.gateway.webhook.UnsafeEndpointAddressException
import org.openfsp.gateway.webhooks.WebhookProperties
import org.openfsp.protocol.OrderIdDerivation
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.filter.UrlHandlerFilter
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.cfg.CoercionAction
import tools.jackson.databind.cfg.CoercionInputShape
import tools.jackson.databind.type.LogicalType
import java.net.UnknownHostException
import java.time.Clock

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class GatewayConfiguration(
    private val properties: GatewayProperties,
    private val providers: ProviderProperties,
    webhooks: WebhookProperties,
    addresses: EndpointAddresses,
    @Value("\${server.ssl.bundle:}") sslBundle: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        check(simulated().isEmpty() || properties.environment == GatewayProperties.Environment.TEST) {
            "a gateway configured against the simulator runs in the test environment"
        }
        check(properties.environment == GatewayProperties.Environment.TEST || !properties.developmentMode) {
            "development mode is only available in the test environment"
        }
        check(properties.adminToken?.startsWith(AdminTokenHashing.PREFIX) ?: true) {
            "openfsp.admin-token holds an Argon2id hash: generate it with the hash command"
        }
        check(properties.bootstrapKey == null || properties.developmentMode) { "a bootstrap API key is only accepted in development mode" }
        if (!properties.developmentMode) {
            check(sslBundle.isNotBlank() || properties.tlsTerminatedByProxy) {
                "the API is served over HTTPS only: set server.ssl.bundle, or openfsp.tls-terminated-by-proxy behind a TLS proxy"
            }
            check(properties.publicBaseUrl.scheme == "https") { "openfsp.public-base-url must use https" }
            check(properties.orderIdDerivationKey != DEVELOPMENT_DERIVATION_KEY) { "the development order id derivation key cannot serve real payments" }
            check(properties.derivationKeyBytes.size >= MIN_DERIVATION_KEY_BYTES) { "the order id derivation key holds at least 256 bits" }
        }
        webhooks.endpoints.forEach { (name, endpoint) ->
            check(endpoint.url.scheme == "https" || properties.developmentMode) { "webhook endpoint $name must use https" }
            try {
                addresses.check(endpoint.url)
            } catch (exception: UnsafeEndpointAddressException) {
                throw IllegalStateException("webhook endpoint $name: ${exception.message}")
            } catch (_: UnknownHostException) {
                log.warn("Webhook endpoint {} does not resolve yet: its address is checked again at each delivery", name)
            }
        }
    }

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun orderIdDerivation(): OrderIdDerivation = OrderIdDerivation(properties.derivationKeyBytes)

    @Bean
    fun strictScalars(): JsonMapperBuilderCustomizer =
        JsonMapperBuilderCustomizer { builder ->
            builder.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            builder.withCoercionConfig(LogicalType.Textual) { config ->
                listOf(CoercionInputShape.Integer, CoercionInputShape.Float, CoercionInputShape.Boolean).forEach { config.setCoercion(it, CoercionAction.Fail) }
            }
        }

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
        const val DEVELOPMENT_DERIVATION_KEY = "ZGV2ZWxvcG1lbnQtb25seS1kZXJpdmF0aW9uLWtleS0wMQ=="
        const val MIN_DERIVATION_KEY_BYTES = 32
    }
}
