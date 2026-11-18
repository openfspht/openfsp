package org.openfsp.simulator.memory

import org.openfsp.simulator.emission.ConformanceRun
import org.openfsp.simulator.emission.spi.ConformanceRuns
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class MemoryConformanceRuns : ConformanceRuns {
    private val held = ConcurrentHashMap<String, ConformanceRun>()

    override fun save(run: ConformanceRun): ConformanceRun = run.also { held[it.id] = it }

    override fun find(id: String): ConformanceRun? = held[id]

    override fun all(): List<ConformanceRun> = held.values.toList()

    override fun purge(before: Instant) {
        held.values.removeIf { it.expiresAt.isBefore(before) }
    }

    override fun clear() {
        held.clear()
    }
}
