package org.openfsp.gateway.payment.spi

import java.time.Duration

interface SynchronizationLimits {
    fun tryAcquire(paymentId: String): Duration?

    fun tryResolve(addressee: String): Duration?
}
