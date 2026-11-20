package org.openfsp.simulator.operator

import java.math.BigDecimal

object Centimes {
    private const val DIGITS = 2

    fun of(decimal: String): BigDecimal = BigDecimal(decimal).movePointRight(DIGITS)

    fun exactly(decimal: String): Long = of(decimal).longValueExact()

    fun toDecimal(centimes: Long): String = BigDecimal.valueOf(centimes, DIGITS).toPlainString()
}
