package org.openfsp.gateway.shared.configuration

import org.apache.catalina.valves.RemoteIpValve
import org.openfsp.gateway.payment.CallbackTokens
import org.openfsp.gateway.providers.credentials.CredentialCipher
import org.openfsp.gateway.shared.security.AdminTokenHashing
import org.openfsp.gateway.shared.security.ApplicationKey
import org.openfsp.server.OrderIdDerivation
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
import java.net.InetAddress
import java.time.Clock
import java.time.ZoneId

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class GatewayConfiguration(
    private val properties: GatewayProperties,
    @Value("\${server.ssl.bundle:}") sslBundle: String,
    hashing: AdminTokenHashing,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        check(properties.environment == GatewayProperties.Environment.TEST || !properties.developmentMode) {
            "development mode is only available in the test environment"
        }
        check(properties.adminToken?.startsWith(AdminTokenHashing.PREFIX) ?: true) {
            "openfsp.admin-token holds an Argon2id hash: generate it with the hash command"
        }
        check(properties.adminHost?.let(HOST_NAME::matches) ?: true) {
            "openfsp.admin-host is a host name, without scheme, port or path: ${properties.adminHost} is not one"
        }
        properties.trustedProxies.forEach { proxy ->
            check(isAddressLiteral(proxy)) { "openfsp.trusted-proxies lists IP addresses only: $proxy is not one" }
        }
        check(properties.bootstrapKey == null || properties.environment == GatewayProperties.Environment.TEST) {
            "a bootstrap API key is only accepted in the test environment"
        }
        if (!properties.developmentMode) {
            check(sslBundle.isNotBlank() || properties.tlsTerminatedByProxy) {
                "the API is served over HTTPS only: set server.ssl.bundle, or openfsp.tls-terminated-by-proxy behind a TLS proxy"
            }
            check(properties.adminHost != null) {
                "openfsp.admin-host names the only host that serves the console and the admin API: set it outside development mode"
            }
            check(properties.publicBaseUrl.scheme == "https") { "openfsp.public-base-url must use https" }
            check(properties.adminToken?.let { hashing.matches(AdminTokenHashing.DEMO_TOKEN, it) } != true) {
                "openfsp.admin-token holds the hash of the published demo token: generate a token of your own with the hash command"
            }
        }
    }

    private fun isAddressLiteral(value: String): Boolean =
        (IPV4.matches(value) || (':' in value && IPV6.matches(value))) && runCatching { InetAddress.getByName(value) }.isSuccess

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
    fun providerCredentialCipher(): CredentialCipher = CredentialCipher(applicationKey.derive(ApplicationKey.Purpose.PROVIDER_CREDENTIALS))

    @Bean
    fun webhookSigningKeyCipher(): CredentialCipher = CredentialCipher(applicationKey.derive(ApplicationKey.Purpose.WEBHOOK_SIGNING_KEYS))

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
            if (properties.tlsTerminatedByProxy && properties.trustedProxies.isNotEmpty()) {
                factory.addEngineValves(
                    RemoteIpValve().apply {
                        internalProxies = properties.trustedProxies.joinToString("|", "^(?:", ")$") { Regex.escape(it) }
                        trustedProxies = null
                        hostHeader = FORWARDED_HOST
                    },
                )
            }
        }

    @Bean
    fun trailingSlashFilter(): UrlHandlerFilter = UrlHandlerFilter.trailingSlashHandler("/**").wrapRequest().build()

    @EventListener(ApplicationReadyEvent::class)
    fun announce() {
        if (properties.developmentMode) log.warn("Development mode: the API is served over plain HTTP")
    }

    private companion object {
        val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
        val IPV6 = Regex("^[0-9A-Fa-f:.]+$")
        val HOST_NAME = Regex("^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$")
        const val FORWARDED_HOST = "X-Forwarded-Host"
    }
}
