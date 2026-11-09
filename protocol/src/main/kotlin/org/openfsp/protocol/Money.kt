package org.openfsp.protocol

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue

enum class Currency(
    @get:JsonValue val code: String,
) {
    HTG("HTG"),
}

data class Money(
    val amount: Long,
    val currency: Currency = Currency.HTG,
) {
    init {
        require(amount in MIN_AMOUNT..MAX_AMOUNT) { "amount must be within ±(2^53 - 1)" }
    }

    @get:JsonIgnore
    val isPositive: Boolean get() = amount > 0

    companion object {
        const val MAX_AMOUNT: Long = (1L shl 53) - 1
        const val MIN_AMOUNT: Long = -MAX_AMOUNT

        fun htg(amount: Long) = Money(amount, Currency.HTG)
    }
}
