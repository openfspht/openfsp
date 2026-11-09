package org.openfsp.gateway.provider

import org.openfsp.protocol.Money
import java.math.BigDecimal
import java.math.RoundingMode

object Amounts {
    fun toDecimal(money: Money): String = BigDecimal.valueOf(money.amount, 2).toPlainString()

    fun fromDecimal(value: String): Money {
        val cents =
            runCatching { BigDecimal(value).movePointRight(2) }
                .getOrElse { throw ProviderException.Error("Unreadable amount from the provider") }
        if (cents.stripTrailingZeros().scale() > 0) throw ProviderException.Error("Amount from the provider is not exact to the cent")
        return runCatching { Money.htg(cents.setScale(0, RoundingMode.UNNECESSARY).longValueExact()) }
            .getOrElse { throw ProviderException.Error("Amount from the provider is out of range") }
    }
}
