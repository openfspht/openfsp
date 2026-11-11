package org.openfsp.gateway.shared.transactions

import org.openfsp.gateway.common.spi.Transactions
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class SpringTransactions(
    private val template: TransactionTemplate,
) : Transactions {
    override fun <T> inTransaction(work: () -> T): T {
        var outcome: Result<T>? = null
        template.executeWithoutResult { outcome = Result.success(work()) }
        return outcome!!.getOrThrow()
    }
}
