package org.openfsp.gateway.shared.configuration

import org.apache.catalina.valves.RemoteIpValve
import org.openfsp.gateway.payment.CallbackTokens
import org.openfsp.gateway.shared.security.AdminTokenHashing
import org.openfsp.gateway.shared.security.ApplicationKey
import org.openfsp.protocol.OrderIdDerivation
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.boot.tomcat.ConfigurableTomcatWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.filter.UrlHandlerFilter
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.cfg.CoercionAction
import tools.jackson.databind.cfg.CoercionInputShape
import tools.jackson.databind.type.LogicalType
import java.time.Clock
import java.time.ZoneId

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class GatewayConfiguration(
    private val properties: GatewayProperties,
    @Value("\${server.ssl.bundle:}") sslBundle: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        check(properties.environment == GatewayProperties.Environment.TEST || !properties.developmentMode) {
            "development mode is only available in the test environment"
        }
        check(properties.adminToken?.startsWith(AdminTokenHashing.PREFIX) ?: true) {
            "openfsp.admin-token holds an Argon2id hash: generate it with the hash command"
        }
        check(properties.bootstrapKey == null || properties.environment == GatewayProperties.Environment.TEST) {
            "a bootstrap API key is only accepted in the test environment"
        }
        if (!properties.developmentMode) {
            check(sslBundle.isNotBlank() || properties.tlsTerminatedByProxy) {
                "the API is served over HTTPS only: set server.ssl.bundle, or openfsp.tls-terminated-by-proxy behind a TLS proxy"
            }
            check(properties.publicBaseUrl.scheme == "https") { "openfsp.public-base-url must use https" }
        }
    }

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun businessTimeZone(): ZoneId = properties.timeZone

    private val applicationKey = ApplicationKey(properties.appKey)

    @Bean
    fun applicationKey(): ApplicationKey = applicationKey

    @Bean
    fun orderIdDerivation(): OrderIdDerivation = OrderIdDerivation(applicationKey.derive(ApplicationKey.Purpose.ORDER_IDS))

    @Bean
    fun callbackTokens(): CallbackTokens = CallbackTokens(applicationKey.derive(ApplicationKey.Purpose.CALLBACK_TOKENS))

    @Bean
    fun strictScalars(): JsonMapperBuilderCustomizer =
        JsonMapperBuilderCustomizer { builder ->
            builder.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            builder.withCoercionConfig(LogicalType.Textual) { config ->
                listOf(CoercionInputShape.Integer, CoercionInputShape.Float, CoercionInputShape.Boolean).forEach { config.setCoercion(it, CoercionAction.Fail) }
            }
        }

    @Bean
    fun forwardedClients(): WebServerFactoryCustomizer<ConfigurableTomcatWebServerFactory> =
        WebServerFactoryCustomizer { factory ->
            if (properties.tlsTerminatedByProxy) factory.addEngineValves(RemoteIpValve())
        }

    @Bean
    fun trailingSlashFilter(): UrlHandlerFilter = UrlHandlerFilter.trailingSlashHandler("/**").wrapRequest().build()

    @EventListener(ApplicationReadyEvent::class)
    fun announce() {
        if (properties.developmentMode) log.warn("Development mode: the API is served over plain HTTP")
    }
}
