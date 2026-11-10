package org.openfsp.gateway.provider

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.protocol.Money
import org.openfsp.protocol.ProblemType

data class AmountLimits(
    val minAmount: Money? = null,
    val maxAmount: Money? = null,
    val amountStep: Money? = null,
) {
    init {
        require(minAmount == null || maxAmount == null || minAmount.amount <= maxAmount.amount) { "min_amount must not exceed max_amount" }
        require(amountStep == null || amountStep.amount > 0) { "amount_step must be positive" }
    }

    val isKnown: Boolean get() = minAmount != null || maxAmount != null || amountStep != null

    fun check(amount: Money) {
        val outside =
            (minAmount != null && amount.amount < minAmount.amount) ||
                (maxAmount != null && amount.amount > maxAmount.amount) ||
                (amountStep != null && amount.amount % amountStep.amount != 0L)
        if (!outside) return
        throw OpenFspException(
            ProblemType.AMOUNT_OUT_OF_RANGE,
            extensions =
                listOfNotNull(
                    minAmount?.let { "min_amount" to it },
                    maxAmount?.let { "max_amount" to it },
                    amountStep?.let { "amount_step" to it },
                ).toMap(),
        )
    }
}
