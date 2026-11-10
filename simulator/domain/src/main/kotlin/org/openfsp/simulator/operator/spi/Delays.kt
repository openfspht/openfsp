package org.openfsp.simulator.operator.spi

import java.time.Duration

fun interface Delays {
    fun pause(duration: Duration)
}
