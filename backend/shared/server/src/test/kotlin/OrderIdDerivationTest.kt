package org.openfsp.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OrderIdDerivationTest {
    @Test
    fun `order ids are 26 lowercase base32 characters derived from the account and the reference`() {
        val derivation = OrderIdDerivation(ByteArray(32) { it.toByte() })
        val orderId = derivation.derive("acct_shop", "INV-2026-00184")
        assertTrue(Regex("^[a-z2-7]{26}$").matches(orderId), orderId)
        assertEquals(orderId, derivation.derive("acct_shop", "INV-2026-00184"))
        assertNotEquals(orderId, derivation.derive("acct_shop", "INV-2026-00185"))
        assertNotEquals(orderId, derivation.derive("acct_other", "INV-2026-00184"))
        assertNotEquals(derivation.derive("acct_a", "bc"), derivation.derive("acct_ab", "c"))
    }

    @Test
    fun `the derivation key holds at least 256 bits`() {
        assertFailsWith<IllegalArgumentException> { OrderIdDerivation(ByteArray(16)) }
    }
}
