package org.openfsp.gateway.providers

import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.protocol.Money
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class DecimalAmounts {
    fun toNumber(money: Money): BigDecimal = BigDecimal.valueOf(money.amount, 2).stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }

    fun fromGrouped(value: String): Money = fromDecimal(value.replace(GROUPING, ""))

    fun fromDecimal(value: String): Money {
        val cents =
            runCatching { BigDecimal(value).movePointRight(2) }
                .getOrElse { throw ProviderFailure.Rejected("Unreadable amount from the provider") }
        if (cents.stripTrailingZeros().scale() > 0) throw ProviderFailure.Rejected("Amount from the provider is not exact to the cent")
        return runCatching { Money.htg(cents.setScale(0, RoundingMode.UNNECESSARY).longValueExact()) }
            .getOrElse { throw ProviderFailure.Rejected("Amount from the provider is out of range") }
    }

    private companion object {
        val GROUPING = Regex("[,\\s]")
    }
}
