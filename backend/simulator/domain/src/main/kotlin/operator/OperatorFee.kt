package org.openfsp.simulator.operator

import java.math.BigDecimal
import java.math.RoundingMode

enum class FeeBearer(
    val wire: String,
) {
    MERCHANT("merchant"),
    PAYER("payer"),
}

data class OperatorFee(
    val rateBasisPoints: Int,
    val bearer: FeeBearer,
) {
    init {
        require(rateBasisPoints in 0..BASIS_POINTS) { "a fee rate goes from 0 to $BASIS_POINTS basis points" }
    }

    fun on(centimes: Long): Long =
        BigDecimal
            .valueOf(centimes)
            .multiply(BigDecimal.valueOf(rateBasisPoints.toLong()))
            .divide(BigDecimal.valueOf(BASIS_POINTS.toLong()), 0, RoundingMode.HALF_UP)
            .longValueExact()

    private companion object {
        const val BASIS_POINTS = 10_000
    }
}
