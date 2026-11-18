package org.openfsp.simulator.memory

import org.openfsp.simulator.common.spi.Sequences
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Component
class MemorySequences : Sequences {
    private val counters = ConcurrentHashMap<String, AtomicLong>()

    override fun next(counter: String): Long = counters.computeIfAbsent(counter) { AtomicLong() }.incrementAndGet()
}
