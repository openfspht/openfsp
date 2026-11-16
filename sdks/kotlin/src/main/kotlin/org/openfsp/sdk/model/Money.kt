package org.openfsp.sdk.model

import org.openfsp.sdk.InvalidRequestException
import java.math.BigDecimal
import java.math.RoundingMode

class Money private constructor(
    val centimes: Long,
) : Comparable<Money> {
    val currency: String get() = HTG

    val gourdes: BigDecimal get() = BigDecimal.valueOf(centimes, SCALE)

    val isPositive: Boolean get() = centimes > 0

    operator fun plus(other: Money): Money = centimes(Math.addExact(centimes, other.centimes))

    operator fun times(quantity: Int): Money = centimes(Math.multiplyExact(centimes, quantity.toLong()))

    override fun compareTo(other: Money): Int = centimes.compareTo(other.centimes)

    override fun equals(other: Any?): Boolean = other is Money && other.centimes == centimes

    override fun hashCode(): Int = centimes.hashCode()

    override fun toString(): String = "${gourdes.toPlainString()} $HTG"

    companion object {
        const val HTG = "HTG"
        private const val SCALE = 2
        private const val MAX_CENTIMES = (1L shl 53) - 1

        @JvmStatic
        fun htg(gourdes: Long): Money = htg(BigDecimal.valueOf(gourdes))

        @JvmStatic
        fun htg(gourdes: String): Money =
            htg(gourdes.trim().toBigDecimalOrNull() ?: throw InvalidRequestException("$gourdes is not an amount in gourdes"))

        @JvmStatic
        fun htg(gourdes: BigDecimal): Money {
            val scaled =
                try {
                    gourdes.setScale(SCALE, RoundingMode.UNNECESSARY)
                } catch (tooPrecise: ArithmeticException) {
                    throw InvalidRequestException("$gourdes has more than two decimals")
                }
            return centimes(scaled.unscaledValue().toLong())
        }

        @JvmStatic
        fun centimes(centimes: Long): Money {
            if (centimes !in -MAX_CENTIMES..MAX_CENTIMES) throw InvalidRequestException("$centimes centimes is out of range")
            return Money(centimes)
        }
    }
}
