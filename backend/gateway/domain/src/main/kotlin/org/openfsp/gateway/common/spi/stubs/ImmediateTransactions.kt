package org.openfsp.gateway.common.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.common.spi.Transactions

@Stub
class ImmediateTransactions : Transactions {
    override fun <T> inTransaction(work: () -> T): T = work()
}
