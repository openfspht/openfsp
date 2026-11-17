package org.openfsp.gateway.payment.spi

import java.time.Duration

fun interface SynchronizationLimits {
    fun tryAcquire(paymentId: String): Duration?
}
