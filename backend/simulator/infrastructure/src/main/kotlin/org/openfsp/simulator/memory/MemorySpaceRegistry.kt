package org.openfsp.simulator.memory

import org.openfsp.simulator.space.Space
import org.openfsp.simulator.space.SpaceId
import org.openfsp.simulator.space.spi.SpaceRegistry
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class MemorySpaceRegistry : SpaceRegistry {
    private val spaces = ConcurrentHashMap<SpaceId, Space>()

    @Synchronized
    override fun open(
        space: Space,
        capacity: Int,
    ): Boolean {
        if (spaces.size >= capacity) return false
        return spaces.putIfAbsent(space.id, space) == null
    }

    override fun find(id: SpaceId): Space? = spaces[id]

    override fun touch(
        id: SpaceId,
        at: Instant,
    ): Space? = spaces.computeIfPresent(id) { _, space -> if (at.isAfter(space.lastUsedAt)) space.copy(lastUsedAt = at) else space }

    override fun remove(id: SpaceId): Boolean = spaces.remove(id) != null

    override fun idleSince(horizon: Instant): List<SpaceId> = spaces.values.filter { !it.lastUsedAt.isAfter(horizon) }.map { it.id }
}
