package org.openfsp.simulator.operator.settlement.spi

import java.time.Duration

fun interface Timers {
    fun after(
        delay: Duration,
        task: () -> Unit,
    )
}
