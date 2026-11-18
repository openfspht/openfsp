package org.openfsp.simulator.common.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.simulator.common.spi.Sequences
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Stub
class InMemorySequences : Sequences {
    private val counters = ConcurrentHashMap<String, AtomicLong>()

    override fun next(counter: String): Long = counters.computeIfAbsent(counter) { AtomicLong() }.incrementAndGet()
}
