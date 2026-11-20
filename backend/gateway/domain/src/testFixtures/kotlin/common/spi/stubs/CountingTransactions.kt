package org.openfsp.gateway.common.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.common.spi.Transactions

@Stub
class CountingTransactions : Transactions {
    var opened = 0
        private set

    override fun <T> inTransaction(work: () -> T): T {
        opened += 1
        return work()
    }

    override fun <T> reading(work: () -> T): T = inTransaction(work)

    override fun <T> readingConsistently(work: () -> T): T = inTransaction(work)
}
