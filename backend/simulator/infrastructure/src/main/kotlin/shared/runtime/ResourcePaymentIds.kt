package org.openfsp.simulator.shared.runtime

import org.openfsp.protocol.ResourceIds
import org.openfsp.simulator.common.spi.PaymentIds
import org.springframework.stereotype.Component

@Component
class ResourcePaymentIds : PaymentIds {
    override fun opaque(): String = ResourceIds.generate("pay")
}
