package org.openfsp.gateway.common.spi

interface OperatorSignals {
    fun signal(
        subject: String,
        message: String,
    )
}
