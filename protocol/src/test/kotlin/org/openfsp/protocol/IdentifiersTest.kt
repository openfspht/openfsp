package org.openfsp.protocol

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IdentifiersTest {
    @Test
    fun `resource ids carry the prefix and fit the ResourceId syntax`() {
        val id = ResourceIds.generate("pay")
        assertTrue(id.startsWith("pay_"))
        assertEquals(4 + 26, id.length)
        assertTrue(ResourceIds.isValid(id))
    }

    @Test
    fun `resource ids are not repeated within the same millisecond`() {
        val clock = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC)
        val ids = List(1_000) { ResourceIds.generate("pay", clock) }.toSet()
        assertEquals(1_000, ids.size)
    }

    @Test
    fun `order ids are 26 lowercase base32 characters derived from the reference`() {
        val derivation = OrderIdDerivation(ByteArray(32) { it.toByte() })
        val orderId = derivation.derive("INV-2026-00184")
        assertTrue(Regex("^[a-z2-7]{26}$").matches(orderId), orderId)
        assertEquals(orderId, derivation.derive("INV-2026-00184"))
        assertNotEquals(orderId, derivation.derive("INV-2026-00185"))
    }

    @Test
    fun `the derivation key holds at least 256 bits`() {
        assertFailsWith<IllegalArgumentException> { OrderIdDerivation(ByteArray(16)) }
    }

    @Test
    fun `references and idempotency keys follow their syntax`() {
        assertTrue(References.isValid("INV-2026-00184"))
        assertTrue(!References.isValid("INV 184"))
        assertTrue(IdempotencyKeys.isValid("0b9c2b6e-6f1d-4c55-9a43-2f1b7d1f3a10"))
        assertTrue(!IdempotencyKeys.isValid(""))
    }
}
