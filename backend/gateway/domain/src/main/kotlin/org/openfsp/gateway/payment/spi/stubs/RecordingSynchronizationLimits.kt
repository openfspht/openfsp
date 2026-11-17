package org.openfsp.gateway.payment.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.payment.spi.SynchronizationLimits
import java.time.Duration

@Stub
class RecordingSynchronizationLimits : SynchronizationLimits {
    val acquired = mutableListOf<String>()
    var refuseWith: Duration? = null

    override fun tryAcquire(paymentId: String): Duration? = refuseWith ?: null.also { acquired += paymentId }
}
