package org.openfsp.simulator.common.spi

import java.time.Instant

fun interface SimulationCleanup {
    fun clear()

    fun clear(account: String) = Unit

    fun purge(before: Instant) = Unit
}
