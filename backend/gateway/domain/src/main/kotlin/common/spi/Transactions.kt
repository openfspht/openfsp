package org.openfsp.gateway.common.spi

interface Transactions {
    fun <T> inTransaction(work: () -> T): T

    fun <T> reading(work: () -> T): T

    fun <T> readingConsistently(work: () -> T): T
}
