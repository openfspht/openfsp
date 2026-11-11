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
}
