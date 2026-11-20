package org.openfsp.protocol.webhook

import java.security.KeyPairGenerator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebhookKeysTest {
    private val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    @Test
    fun `a published key decodes back to the same public key`() {
        val jwk = WebhookJwk.of("whk_1", keys.public, KeyStatus.ACTIVE)
        assertEquals(43, jwk.x.length)
        assertEquals("active", jwk.openfspStatus)
        assertContentEquals(keys.public.encoded, jwk.publicKeyOrNull()!!.encoded)
    }

    @Test
    fun `a key of another type or curve is not used`() {
        val jwk = WebhookJwk.of("whk_1", keys.public, KeyStatus.ACTIVE)
        assertNull(jwk.copy(crv = "X25519").publicKeyOrNull())
        assertNull(jwk.copy(alg = "ES256").publicKeyOrNull())
        assertNull(jwk.copy(x = "AAAA").publicKeyOrNull())
    }

    @Test
    fun `a P-256 key decodes from its coordinates and must lie on the curve`() {
        val ec = KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val (x, y) = P256Keys.toCoordinates(ec.public as java.security.interfaces.ECPublicKey)
        val jwk = WebhookJwk(kid = "ec-1", kty = "EC", crv = "P-256", alg = "ES256", x = x, y = y, openfspStatus = "active")
        assertContentEquals(ec.public.encoded, jwk.publicKeyOrNull()!!.encoded)
        assertEquals(WebhookAlgorithm.ECDSA_P256_SHA256, WebhookAlgorithm.of(jwk.publicKeyOrNull()!!))
        assertNull(jwk.copy(alg = "EdDSA").publicKeyOrNull())
        assertNull(jwk.copy(use = "enc").publicKeyOrNull())
        assertNull(jwk.copy(y = x).publicKeyOrNull())
    }
}
