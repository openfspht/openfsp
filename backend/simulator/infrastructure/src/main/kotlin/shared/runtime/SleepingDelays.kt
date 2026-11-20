package org.openfsp.simulator.shared.runtime

import org.openfsp.simulator.common.spi.Delays
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class SleepingDelays : Delays {
    override fun pause(duration: Duration) {
        if (!duration.isZero) Thread.sleep(duration)
    }
}
