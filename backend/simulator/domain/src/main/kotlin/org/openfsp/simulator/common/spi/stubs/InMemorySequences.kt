package org.openfsp.simulator.common.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.simulator.common.spi.Sequences
import org.openfsp.simulator.space.SpaceId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Stub
class InMemorySequences : Sequences {
    private val counters = ConcurrentHashMap<Pair<SpaceId, String>, AtomicLong>()

    override fun next(
        space: SpaceId,
        counter: String,
    ): Long = counters.computeIfAbsent(space to counter) { AtomicLong() }.incrementAndGet()

    override fun clear(space: SpaceId) {
        counters.keys.removeIf { it.first == space }
    }
}
