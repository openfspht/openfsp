package org.openfsp.gateway.configuration

import org.openfsp.gateway.providers.ProviderProperties
import org.openfsp.gateway.webhook.EndpointAddresses
import org.openfsp.gateway.webhook.EndpointRules
import org.openfsp.gateway.webhook.spi.stubs.StaticHostResolver
import org.openfsp.gateway.webhooks.WebhookProperties
import java.net.InetAddress
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProductionGuardTest {
    private val strongKey = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 9 })

    private fun live(
        publicBaseUrl: String = "https://gateway.example",
        derivationKey: String = strongKey,
        tlsTerminatedByProxy: Boolean = false,
        adminToken: String? = null,
    ) = GatewayProperties(
        environment = GatewayProperties.Environment.LIVE,
        developmentMode = false,
        publicBaseUrl = URI(publicBaseUrl),
        orderIdDerivationKey = derivationKey,
        tlsTerminatedByProxy = tlsTerminatedByProxy,
        adminToken = adminToken,
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
    ) = GatewayConfiguration(
        properties,
        ProviderProperties(),
        webhooks,
        EndpointAddresses(resolver, EndpointRules(properties.developmentMode, properties.developmentMode)),
        sslBundle,
    )

    private fun endpoint(url: String) = WebhookProperties(endpoints = mapOf("shop" to WebhookProperties.Endpoint(URI(url), setOf("payment.succeeded"))))

    @Test
    fun `a live gateway refuses plain HTTP unless a TLS proxy is declared`() {
        assertTrue(assertFailsWith<IllegalStateException> { start(live()) }.message!!.contains("HTTPS"))
        start(live(tlsTerminatedByProxy = true))
        start(live(), sslBundle = "gateway")
    }

    @Test
    fun `a live gateway refuses an http public URL and the development derivation key`() {
        assertFailsWith<IllegalStateException> { start(live(publicBaseUrl = "http://gateway.example", tlsTerminatedByProxy = true)) }
        assertFailsWith<IllegalStateException> {
            start(live(derivationKey = "ZGV2ZWxvcG1lbnQtb25seS1kZXJpdmF0aW9uLWtleS0wMQ==", tlsTerminatedByProxy = true))
        }
    }

    @Test
    fun `the admin token is only accepted as an Argon2id hash`() {
        assertTrue(assertFailsWith<IllegalStateException> { start(live(tlsTerminatedByProxy = true, adminToken = "plain-admin-token")) }.message!!.contains("Argon2id"))
        start(live(tlsTerminatedByProxy = true, adminToken = "\$argon2id\$v=19\$m=65536,t=3,p=4\$c2FsdA\$aGFzaA"))
    }

    @Test
    fun `a live gateway refuses a configured webhook that resolves to a private or metadata address`() {
        listOf("https://internal.example/webhooks", "https://127.0.0.1/webhooks", "https://169.254.169.254/latest").forEach { url ->
            assertTrue(assertFailsWith<IllegalStateException>(url) { start(live(tlsTerminatedByProxy = true), webhooks = endpoint(url)) }.message!!.contains("not a public address"))
        }
        start(live(tlsTerminatedByProxy = true), webhooks = endpoint("https://shop.example/webhooks"))
        start(live(tlsTerminatedByProxy = true), webhooks = endpoint("https://not-yet-resolvable.example/webhooks"))
    }
}
