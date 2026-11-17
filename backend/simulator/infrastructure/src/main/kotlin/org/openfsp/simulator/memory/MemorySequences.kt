package org.openfsp.simulator.memory

import org.openfsp.simulator.common.spi.Sequences
import org.openfsp.simulator.space.SpaceId
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Component
class MemorySequences : Sequences {
    private val counters = ConcurrentHashMap<Pair<SpaceId, String>, AtomicLong>()

    override fun next(
        space: SpaceId,
        counter: String,
    ): Long = counters.computeIfAbsent(space to counter) { AtomicLong() }.incrementAndGet()

    override fun clear(space: SpaceId) {
        counters.keys.removeIf { it.first == space }
    }
}
