package org.openfsp.simulator.dialects.openfsp

import org.openfsp.protocol.Money
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class OpenFspAmounts {
    fun toDecimal(money: Money): String = BigDecimal.valueOf(money.amount, CENT_DIGITS).toPlainString()

    fun toMoney(decimal: String): Money = toMoney(BigDecimal(decimal))

    fun toMoney(decimal: BigDecimal): Money = Money.htg(decimal.movePointRight(CENT_DIGITS).longValueExact())

    private companion object {
        const val CENT_DIGITS = 2
    }
}
