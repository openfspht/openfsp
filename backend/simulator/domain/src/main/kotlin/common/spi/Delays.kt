package org.openfsp.simulator.common.spi

import java.time.Duration

fun interface Delays {
    fun pause(duration: Duration)
}
