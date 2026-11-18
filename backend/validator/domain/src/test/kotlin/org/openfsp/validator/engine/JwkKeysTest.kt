package org.openfsp.validator.engine

import org.openfsp.protocol.webhook.Ed25519Keys
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JwkKeysTest {
    private fun coordinate(value: BigInteger): String {
        val bytes = value.toByteArray().let { if (it.size > 32) it.copyOfRange(it.size - 32, it.size) else ByteArray(32 - it.size) + it }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    @Test
    fun `a P-256 key published with x and y is the key that signs`() {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val point = (pair.public as ECPublicKey).w
        val x = coordinate(point.affineX)
        val y = coordinate(point.affineY)

        assertEquals(pair.public, JwkKeys.p256("P-256", "ES256", x, y))
        assertNull(JwkKeys.p256("P-256", "ES256", x, null))
        assertNull(JwkKeys.p256("P-384", "ES256", x, y))
        assertNull(JwkKeys.p256("P-256", "ES256", x, x))
    }

    @Test
    fun `an Ed25519 key is read from x alone and refused under another algorithm`() {
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val x = Ed25519Keys.toRaw(pair.public)

        assertEquals(pair.public, JwkKeys.ed25519("Ed25519", "EdDSA", x))
        assertNull(JwkKeys.ed25519("Ed25519", "ES256", x))
    }
}
