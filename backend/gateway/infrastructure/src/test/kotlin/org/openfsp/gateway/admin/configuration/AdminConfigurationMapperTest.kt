package org.openfsp.gateway.admin.configuration

import org.openfsp.gateway.admin.web.ProviderResource
import org.openfsp.gateway.admin.web.ProviderResourceMapper
import org.openfsp.gateway.capability.ProviderCapabilities
import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.webhook.PublishedSigningKey
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.api.CapabilitiesResource
import org.openfsp.protocol.api.ConfigurationResource
import org.openfsp.protocol.webhook.KeyStatus
import java.net.URI
import java.security.KeyPairGenerator
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdminConfigurationMapperTest {
    private val at = Instant.parse("2026-11-17T10:15:30Z")
    private val configuration = ConfigurationResource("https://gateway.example", "0.1.0", "a", "p", "c", null, emptyList())
    private val capabilities = CapabilitiesResource("0.1.0", emptyList())
    private val publicKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public

    @Test
    fun `the signing state lists each published key and the rotation period in minutes`() {
        val key = PublishedSigningKey("kid_1", KeyStatus.ACTIVE, at, null, publicKey)
        val resource = AdminConfigurationMapper().toResource(configuration, capabilities, listOf(key), Duration.ofHours(2), at.plusSeconds(7200))
        assertEquals(SigningResource(listOf(SigningKeyState("kid_1", "active", at, null)), 120, at.plusSeconds(7200)), resource.signing)
        assertEquals(configuration, resource.configuration)
    }

    @Test
    fun `no rotation period below one minute is shown`() {
        assertNull(AdminConfigurationMapper().toResource(configuration, capabilities, emptyList(), Duration.ofSeconds(30), null).signing.rotateEveryMinutes)
        assertNull(AdminConfigurationMapper().toResource(configuration, capabilities, emptyList(), null, null).signing.rotateEveryMinutes)
    }

    @Test
    fun `a provider of the console names its display name, logo and capabilities`() {
        val descriptor = ProviderDescriptor("Alpha", setOf(ProviderDescriptor.PAYMENTS), setOf(NextAction.Type.REDIRECT), false, logoUrl = URI("https://cdn.example/a.svg"))
        val listed = ProviderResourceMapper().toCollection(listOf(ProviderCapabilities("alpha", descriptor, listOf("payments"), AmountLimits())))
        assertEquals(listOf(ProviderResource("alpha", "Alpha", "https://cdn.example/a.svg", listOf("payments"))), listed.data)
    }
}
