package org.openfsp.validator.engine.spi

import java.time.Duration

fun interface Pause {
    fun pause(duration: Duration)
}
