package org.openfsp.simulator.common.spi

import java.time.Instant

fun interface SimulationCleanup {
    fun clear()

    fun purge(before: Instant) = Unit
}
