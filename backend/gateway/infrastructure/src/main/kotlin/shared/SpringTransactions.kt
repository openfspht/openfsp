package org.openfsp.gateway.shared

import org.openfsp.gateway.common.spi.Transactions
import org.springframework.stereotype.Component
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

@Component
class SpringTransactions(
    private val template: TransactionTemplate,
) : Transactions {
    private val readOnly = TransactionTemplate(template.transactionManager!!).apply { isReadOnly = true }
    private val consistent =
        TransactionTemplate(template.transactionManager!!).apply {
            isReadOnly = true
            isolationLevel = TransactionDefinition.ISOLATION_REPEATABLE_READ
        }

    override fun <T> inTransaction(work: () -> T): T = run(template, work)

    override fun <T> reading(work: () -> T): T = run(readOnly, work)

    override fun <T> readingConsistently(work: () -> T): T = run(consistent, work)

    private fun <T> run(
        template: TransactionTemplate,
        work: () -> T,
    ): T {
        var outcome: Result<T>? = null
        template.executeWithoutResult { outcome = Result.success(work()) }
        return outcome!!.getOrThrow()
    }
}
