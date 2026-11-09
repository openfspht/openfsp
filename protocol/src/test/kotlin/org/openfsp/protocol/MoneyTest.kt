package org.openfsp.protocol

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoneyTest {
    @Test
    fun `accepts the largest exact JSON integer`() {
        assertTrue(Money.htg(Money.MAX_AMOUNT).isPositive)
    }

    @Test
    fun `rejects amounts beyond 2^53 - 1`() {
        assertFailsWith<IllegalArgumentException> { Money.htg(Money.MAX_AMOUNT + 1) }
        assertFailsWith<IllegalArgumentException> { Money.htg(Money.MIN_AMOUNT - 1) }
    }

    @Test
    fun `zero is not a positive amount`() {
        assertFalse(Money.htg(0).isPositive)
    }
}
