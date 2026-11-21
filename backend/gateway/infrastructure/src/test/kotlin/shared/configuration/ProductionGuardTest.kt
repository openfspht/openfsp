package org.openfsp.gateway.shared.configuration

import org.apache.catalina.valves.RemoteIpValve
import org.openfsp.gateway.conformance.ConformanceProperties
import org.openfsp.gateway.providers.ProviderConfiguration
import org.openfsp.gateway.providers.ProviderProperties
import org.openfsp.gateway.shared.security.AdminTokenHashing
import org.openfsp.gateway.webhook.EndpointAddresses
import org.openfsp.gateway.webhook.EndpointRules
import org.openfsp.gateway.webhook.spi.stubs.StaticHostResolver
import org.openfsp.gateway.webhooks.ConfiguredEndpointCheck
import org.openfsp.gateway.webhooks.WebhookProperties
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory
import java.net.InetAddress
import java.net.URI
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import org.springframework.mock.env.MockEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProductionGuardTest {
    private val strongKey = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 9 })

    private fun live(
        publicBaseUrl: String = "https://gateway.example",
        appKey: String = strongKey,
        tlsTerminatedByProxy: Boolean = false,
        adminToken: String? = null,
        bootstrapKey: String? = null,
        adminHost: String? = "console.gateway.example",
    ) = GatewayProperties(
        environment = GatewayProperties.Environment.LIVE,
        developmentMode = false,
        publicBaseUrl = URI(publicBaseUrl),
        appKey = appKey,
        tlsTerminatedByProxy = tlsTerminatedByProxy,
        adminToken = adminToken,
        bootstrapKey = bootstrapKey,
        adminHost = adminHost,
    )

    private val resolver =
        StaticHostResolver().apply {
            addresses["shop.example"] = listOf(InetAddress.getByName("93.184.215.14"))
            addresses["internal.example"] = listOf(InetAddress.getByName("10.0.0.5"))
        }

    private fun start(
        properties: GatewayProperties,
        sslBundle: String = "",
        webhooks: WebhookProperties = WebhookProperties(),
        demo: Boolean = false,
    ): GatewayConfiguration {
        providers(ProviderProperties(), properties, demo = demo)
        ConfiguredEndpointCheck(properties, webhooks, EndpointAddresses(resolver, EndpointRules(properties.developmentMode)))
        return GatewayConfiguration(properties, sslBundle, AdminTokenHashing(), DemoMode(demo))
    }

    private fun providers(
        providers: ProviderProperties,
        properties: GatewayProperties,
        demo: Boolean = false,
        conformance: Boolean = false,
    ) = ProviderConfiguration(providers, properties, DemoMode(demo), ConformanceProperties(enabled = conformance))

    private fun endpoint(url: String) = WebhookProperties(endpoints = mapOf("shop" to WebhookProperties.Endpoint(url = URI(url), events = setOf("payment.succeeded"))))

    @Test
    fun `a live gateway refuses plain HTTP unless a TLS proxy is declared`() {
        assertTrue(assertFailsWith<IllegalStateException> { start(live()) }.message!!.contains("HTTPS"))
        start(live(tlsTerminatedByProxy = true))
        start(live(), sslBundle = "gateway")
    }

    @Test
    fun `a gateway outside development mode names the host of its console`() {
        assertTrue(assertFailsWith<IllegalStateException> { start(live(tlsTerminatedByProxy = true, adminHost = null)) }.message!!.contains("openfsp.admin-host"))
        listOf("https://console.example", "console.example:8443", "console.example/admin", "console example", "").forEach { host ->
            assertTrue(assertFailsWith<IllegalStateException>(host) { start(live(tlsTerminatedByProxy = true, adminHost = host)) }.message!!.contains("openfsp.admin-host"))
        }
        start(live(tlsTerminatedByProxy = true, adminHost = "console.example"))
    }

    @Test
    fun `a live gateway refuses an http public URL`() {
        assertFailsWith<IllegalStateException> { start(live(publicBaseUrl = "http://gateway.example", tlsTerminatedByProxy = true)) }
    }

    @Test
    fun `a gateway refuses an application key shorter than 256 bits or not in base64`() {
        listOf(java.util.Base64.getEncoder().encodeToString(ByteArray(16) { 9 }), "not base64!", "").forEach { key ->
            assertTrue(assertFailsWith<IllegalStateException>(key) { start(live(appKey = key, tlsTerminatedByProxy = true)) }.message!!.contains("openfsp.app-key"))
        }
    }

    @Test
    fun `a gateway refuses an application key published in the repository history`() {
        listOf("development-only-application-key", "development-only-credentials-k01", "development-only-derivation-key-01").forEach { fixture ->
            val key = java.util.Base64.getEncoder().encodeToString(fixture.toByteArray())
            assertTrue(assertFailsWith<IllegalStateException>(fixture) { start(live(appKey = key, tlsTerminatedByProxy = true)) }.message!!.contains("published"))
        }
    }

    @Test
    fun `the admin token is only accepted as an Argon2id hash`() {
        assertTrue(assertFailsWith<IllegalStateException> { start(live(tlsTerminatedByProxy = true, adminToken = "plain-admin-token")) }.message!!.contains("Argon2id"))
        start(live(tlsTerminatedByProxy = true, adminToken = "\$argon2id\$v=19\$m=65536,t=3,p=4\$c2FsdA\$aGFzaA"))
    }

    @Test
    fun `a live gateway refuses any hash of the published demo admin token`() {
        listOf(PUBLISHED_DEMO_HASH, AdminTokenHashing().hash(AdminTokenHashing.DEMO_TOKEN)).forEach { hash ->
            assertTrue(assertFailsWith<IllegalStateException> { start(live(tlsTerminatedByProxy = true, adminToken = hash)) }.message!!.contains("demo token"))
        }
        start(live(tlsTerminatedByProxy = true, adminToken = AdminTokenHashing().hash("a token of the operator")))
    }

    @Test
    fun `the demo profile accepts the published demo admin token in the test environment only`() {
        val demo = YamlPropertySourceLoader().load("demo", ClassPathResource("application-demo.yaml")).single()
        assertEquals(PUBLISHED_DEMO_HASH, demo.getProperty("openfsp.admin-token"))
        assertEquals("test", demo.getProperty("openfsp.environment"))
        start(live(tlsTerminatedByProxy = true, adminToken = PUBLISHED_DEMO_HASH).copy(environment = GatewayProperties.Environment.TEST), demo = true)
        assertTrue(assertFailsWith<IllegalStateException> { start(live(tlsTerminatedByProxy = true), demo = true) }.message!!.contains("demo"))
    }

    @Test
    fun `without the demo profile the published demo credentials are refused, in development mode too`() {
        val development = live(tlsTerminatedByProxy = true).copy(environment = GatewayProperties.Environment.TEST, developmentMode = true, publicBaseUrl = URI("http://127.0.0.1:8080"))
        assertTrue(assertFailsWith<IllegalStateException> { start(development.copy(adminToken = PUBLISHED_DEMO_HASH)) }.message!!.contains("demo token"))
        assertTrue(assertFailsWith<IllegalStateException> { start(development.copy(bootstrapKey = GatewayProperties.PUBLISHED_DEMO_KEY)) }.message!!.contains("demo key"))
        start(development.copy(adminToken = PUBLISHED_DEMO_HASH, bootstrapKey = GatewayProperties.PUBLISHED_DEMO_KEY), demo = true)
    }

    @Test
    fun `the demo serves simulated providers only`() {
        val test = live(tlsTerminatedByProxy = true).copy(environment = GatewayProperties.Environment.TEST)
        val sandbox = ProviderProperties(providers = mapOf("moncash" to ProviderProperties.Provider(type = "moncash", baseUrl = URI("https://sandbox.example"))))
        assertTrue(assertFailsWith<IllegalStateException> { providers(sandbox, test, demo = true) }.message!!.contains("[moncash]"))
        providers(ProviderProperties(providers = mapOf("mock_moncash" to ProviderProperties.Provider(type = "moncash", baseUrl = URI("https://x.example"), simulated = true))), test, demo = true)
    }

    @Test
    fun `conformance preparation serves simulated providers only`() {
        val test = live(tlsTerminatedByProxy = true).copy(environment = GatewayProperties.Environment.TEST)
        val sandbox = ProviderProperties(providers = mapOf("natcash" to ProviderProperties.Provider(type = "natcash", baseUrl = URI("https://sandbox.example"))))
        assertTrue(assertFailsWith<IllegalStateException> { providers(sandbox, test, conformance = true) }.message!!.contains("[natcash]"))
        providers(sandbox, test)
    }

    @Test
    fun `a provider outside the simulator and development mode is reached over https only`() {
        val plain = ProviderProperties(providers = mapOf("operator" to ProviderProperties.Provider(type = "openfsp", baseUrl = URI("http://provider.example"))))
        assertTrue(assertFailsWith<IllegalStateException> { providers(plain, live(tlsTerminatedByProxy = true)) }.message!!.contains("https only"))
        providers(ProviderProperties(providers = mapOf("operator" to ProviderProperties.Provider(type = "openfsp", baseUrl = URI("HTTPS://provider.example")))), live(tlsTerminatedByProxy = true))
        providers(plain, live(tlsTerminatedByProxy = true).copy(environment = GatewayProperties.Environment.TEST, developmentMode = true, publicBaseUrl = URI("http://127.0.0.1:8080")))
    }

    @Test
    fun `the demo follows the active Spring profile, not a property`() {
        val bound = MockEnvironment().withProperty("openfsp.demo", "true")
        assertFalse(DemoMode(bound).active)
        assertTrue(DemoMode(MockEnvironment().apply { setActiveProfiles("development", DemoMode.PROFILE) }).active)
        assertFalse(DemoMode(MockEnvironment().apply { setDefaultProfiles("simulated", DemoMode.PROFILE) }).active)
        assertEquals(null, YamlPropertySourceLoader().load("demo", ClassPathResource("application-demo.yaml")).single().getProperty("openfsp.demo"))
    }

    @Test
    fun `a gateway started without any profile runs with the live defaults`() {
        val defaults = YamlPropertySourceLoader().load("application", ClassPathResource("application.yaml")).single()
        assertNull(defaults.getProperty("spring.profiles.default"))
        assertEquals("live", defaults.getProperty("openfsp.environment"))
        assertEquals(false, defaults.getProperty("openfsp.development-mode"))
    }

    @Test
    fun `a live gateway refuses a configured webhook that resolves to a private or metadata address`() {
        listOf("https://internal.example/webhooks", "https://127.0.0.1/webhooks", "https://169.254.169.254/latest").forEach { url ->
            assertTrue(assertFailsWith<IllegalStateException>(url) { start(live(tlsTerminatedByProxy = true), webhooks = endpoint(url)) }.message!!.contains("not a public address"))
        }
        start(live(tlsTerminatedByProxy = true), webhooks = endpoint("https://shop.example/webhooks"))
        start(live(tlsTerminatedByProxy = true), webhooks = endpoint("https://not-yet-resolvable.example/webhooks"))
    }

    @Test
    fun `behind a TLS proxy the forwarded headers are read only from the configured proxy addresses`() {
        val proxied = TomcatServletWebServerFactory()
        start(live(tlsTerminatedByProxy = true).copy(trustedProxies = listOf("10.0.0.2", "fd00::2"))).forwardedClients().customize(proxied)
        val valve = proxied.engineValves.filterIsInstance<RemoteIpValve>().single()
        assertEquals("X-Forwarded-Host", valve.hostHeader)
        val trusted = Regex(valve.internalProxies)
        assertTrue(trusted.matches("10.0.0.2") && trusted.matches("fd00::2"))
        assertFalse(trusted.matches("10.0.0.3") || trusted.matches("192.168.1.1") || trusted.matches("10.0.0.20"))

        val unlisted = TomcatServletWebServerFactory()
        start(live(tlsTerminatedByProxy = true)).forwardedClients().customize(unlisted)
        assertTrue(unlisted.engineValves.none { it is RemoteIpValve })
        assertFailsWith<IllegalStateException> { start(live(tlsTerminatedByProxy = true).copy(trustedProxies = listOf("proxy.internal"))) }

        val direct = TomcatServletWebServerFactory()
        start(live(), sslBundle = "gateway").forwardedClients().customize(direct)
        assertTrue(direct.engineValves.none { it is RemoteIpValve })
    }

    @Test
    fun `a live gateway refuses a provider declared simulated, whatever its name`() {
        val simulated = ProviderProperties(providers = mapOf("moncash" to ProviderProperties.Provider(type = "moncash", baseUrl = URI("https://x.example"), simulated = true)))
        assertTrue(assertFailsWith<IllegalStateException> { providers(simulated, live(tlsTerminatedByProxy = true)) }.message!!.contains("simulator"))
        providers(ProviderProperties(providers = mapOf("mock_named" to ProviderProperties.Provider(type = "moncash", baseUrl = URI("https://x.example")))), live(tlsTerminatedByProxy = true))
    }

    @Test
    fun `a live gateway refuses a bootstrap API key`() {
        val refused = assertFailsWith<IllegalStateException> { start(live(tlsTerminatedByProxy = true, bootstrapKey = "ofsp_live_" + "A".repeat(40))) }
        assertTrue(refused.message!!.contains("bootstrap"))
    }

    @Test
    fun `a test gateway outside development mode accepts a bootstrap API key`() {
        start(live(tlsTerminatedByProxy = true, bootstrapKey = "ofsp_test_" + "A".repeat(40)).copy(environment = GatewayProperties.Environment.TEST))
    }

    private companion object {
        const val PUBLISHED_DEMO_HASH = "\$argon2id\$v=19\$m=65536,t=3,p=4\$PEua5g9jsR2Ot2g7bhkBWT4vxejdDAl8jlbiptXsNzI\$ZlQNIj02jEudsOtGDc/XRMcHIW2jOfSAzq/68WI+x5c"
    }
}
