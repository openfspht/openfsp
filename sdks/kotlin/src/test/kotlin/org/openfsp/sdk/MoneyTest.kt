package org.openfsp.sdk

import org.openfsp.sdk.model.Money
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {
    @Test
    fun `reads gourdes and keeps centimes`() {
        assertEquals(125_000, Money.htg(1250).centimes)
        assertEquals(125_050, Money.htg("1250.5").centimes)
        assertEquals(7, Money.htg(BigDecimal("0.07")).centimes)
        assertEquals(BigDecimal("1250.50"), Money.centimes(125_050).gourdes)
    }

    @Test
    fun `refuses more than two decimals`() {
        assertFailsWith<InvalidRequestException> { Money.htg("1.234") }
        assertFailsWith<InvalidRequestException> { Money.htg("12,50") }
    }

    @Test
    fun `adds, multiplies, compares and prints`() {
        assertEquals(Money.htg(350), Money.htg(100) * 3 + Money.htg(50))
        assertEquals(true, Money.htg(1) < Money.htg(2))
        assertEquals("1250.50 HTG", Money.htg("1250.5").toString())
        assertEquals("-0.07 HTG", Money.centimes(-7).toString())
    }
}
