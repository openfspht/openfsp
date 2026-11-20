package org.openfsp.simulator.operator

import java.math.BigDecimal

data class AmountLimits(
    val minAmount: Long,
    val maxAmount: Long,
    val amountStep: Long,
) {
    init {
        require(minAmount in 1..maxAmount) { "min-amount must be positive and at most max-amount" }
        require(amountStep > 0) { "amount-step must be positive" }
    }

    fun admits(amount: String): Boolean {
        val cents = Centimes.of(amount)
        return cents >= BigDecimal.valueOf(minAmount) && cents <= BigDecimal.valueOf(maxAmount) && cents.remainder(BigDecimal.valueOf(amountStep)).signum() == 0
    }

    companion object {
        const val OUT_OF_RANGE = "AMOUNT_OUT_OF_RANGE"
    }
}
