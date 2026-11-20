package org.openfsp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DigestsTest {
    private val digests = Digests()

    @Test
    fun `hmac and sha256 are written as lowercase hex`() {
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843", digests.hmacSha256Hex("Jefe", "what do ya want for nothing?"))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", digests.sha256Hex("abc"))
    }

    @Test
    fun `hex digests compare without regard to case`() {
        val digest = digests.sha256Hex("abc")
        assertTrue(digests.hexEquals(digest, digest.uppercase()))
        assertTrue(digests.hexEquals(digest.uppercase(), digest))
        assertFalse(digests.hexEquals(digest, digest.dropLast(1) + "0"))
        assertFalse(digests.hexEquals(digest, null))
    }

    @Test
    fun `secrets compare exactly`() {
        assertTrue(digests.secretEquals("Bearer key", "Bearer key"))
        assertFalse(digests.secretEquals("Bearer key", "bearer key"))
        assertFalse(digests.secretEquals("Bearer key", "Bearer key "))
        assertFalse(digests.secretEquals("Bearer key", null))
    }
}
