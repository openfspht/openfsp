package org.openfsp.gateway.providers.credentials

import org.junit.jupiter.api.Test
import javax.crypto.AEADBadTagException
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class CredentialCipherTest {
    private val cipher = CredentialCipher(ByteArray(32) { 1 })
    private val plaintext = """{"client-secret":"s3cr3t-value"}""".toByteArray()

    @Test
    fun `sealed credentials open only in their own context and under their own key`() {
        val sealed = cipher.seal(plaintext, "acct_shop/moncash")
        assertContentEquals(plaintext, cipher.open(sealed, "acct_shop/moncash"))
        assertFalse(String(sealed.ciphertext, Charsets.ISO_8859_1).contains("s3cr3t-value"))
        assertFailsWith<AEADBadTagException> { cipher.open(sealed, "acct_other/moncash") }
        assertFailsWith<AEADBadTagException> { cipher.open(sealed.copy(ciphertext = sealed.ciphertext.copyOf().also { it[0] = (it[0] + 1).toByte() }), "acct_shop/moncash") }
        assertFailsWith<IllegalStateException> { CredentialCipher(ByteArray(32) { 2 }).open(sealed, "acct_shop/moncash") }
    }

    @Test
    fun `every seal draws a fresh nonce and the key holds 256 bits`() {
        assertNotEquals(cipher.seal(plaintext, "c").nonce.toList(), cipher.seal(plaintext, "c").nonce.toList())
        assertFailsWith<IllegalArgumentException> { CredentialCipher(ByteArray(16)) }
    }
}
