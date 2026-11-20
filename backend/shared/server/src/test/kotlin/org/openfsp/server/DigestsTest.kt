package org.openfsp.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DigestsTest {
    @Test
    fun `hmac and sha256 are written as lowercase hex`() {
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843", Digests.hmacSha256Hex("Jefe", "what do ya want for nothing?"))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Digests.sha256Hex("abc"))
    }

    @Test
    fun `hex digests compare without regard to case`() {
        val digest = Digests.sha256Hex("abc")
        assertTrue(Digests.hexEquals(digest, digest.uppercase()))
        assertTrue(Digests.hexEquals(digest.uppercase(), digest))
        assertFalse(Digests.hexEquals(digest, digest.dropLast(1) + "0"))
        assertFalse(Digests.hexEquals(digest, null))
    }

    @Test
    fun `secrets compare exactly`() {
        assertTrue(Digests.secretEquals("Bearer key", "Bearer key"))
        assertFalse(Digests.secretEquals("Bearer key", "bearer key"))
        assertFalse(Digests.secretEquals("Bearer key", "Bearer key "))
        assertFalse(Digests.secretEquals("Bearer key", null))
    }
}
