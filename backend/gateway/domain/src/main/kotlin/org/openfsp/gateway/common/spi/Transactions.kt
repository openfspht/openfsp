package org.openfsp.gateway.common.spi

interface Transactions {
    fun <T> inTransaction(work: () -> T): T
}
