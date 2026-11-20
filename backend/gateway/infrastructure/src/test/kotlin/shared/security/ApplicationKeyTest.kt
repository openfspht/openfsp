package org.openfsp.gateway.shared.security

import java.util.Base64
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ApplicationKeyTest {
    private val hex = HexFormat.of()

    @Test
    fun `the derivation follows the HKDF-SHA256 test vectors of RFC 5869`() {
        val okm =
            ApplicationKey.hkdf(
                salt = hex.parseHex("000102030405060708090a0b0c"),
                ikm = hex.parseHex("0b".repeat(22)),
                info = hex.parseHex("f0f1f2f3f4f5f6f7f8f9"),
                length = 42,
            )
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", hex.formatHex(okm))
        val empty = ApplicationKey.hkdf(ByteArray(32), hex.parseHex("0b".repeat(22)), ByteArray(0), 42)
        assertEquals("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8", hex.formatHex(empty))
    }

    @Test
    fun `each purpose receives its own key, stable for a given application key`() {
        val encoded = Base64.getEncoder().encodeToString(ByteArray(32) { 5 })
        val key = ApplicationKey(encoded)
        val derived = ApplicationKey.Purpose.entries.map { key.derive(it).toList() }
        assertEquals(derived.size, derived.toSet().size)
        assertContentEquals(key.derive(ApplicationKey.Purpose.ORDER_IDS), ApplicationKey(encoded).derive(ApplicationKey.Purpose.ORDER_IDS))
        assertFalse(key.derive(ApplicationKey.Purpose.ORDER_IDS).contentEquals(ByteArray(32) { 5 }))
    }
}
