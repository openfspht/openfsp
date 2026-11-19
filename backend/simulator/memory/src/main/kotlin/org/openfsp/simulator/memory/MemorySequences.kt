package org.openfsp.simulator.memory

import org.openfsp.simulator.common.spi.Sequences
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class MemorySequences : Sequences {
    private val counters = ConcurrentHashMap<String, AtomicLong>()

    override fun next(counter: String): Long = counters.computeIfAbsent(counter) { AtomicLong() }.incrementAndGet()
}
