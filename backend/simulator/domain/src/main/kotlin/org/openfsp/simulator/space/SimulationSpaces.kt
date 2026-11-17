package org.openfsp.simulator.space

import org.openfsp.ddd.DomainService
import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.common.spi.Sequences
import org.openfsp.simulator.control.api.ControlSimulation
import org.openfsp.simulator.space.api.ManageSpaces
import org.openfsp.simulator.space.spi.SpaceRegistry
import java.time.Duration

@DomainService
class SimulationSpaces(
    private val registry: SpaceRegistry,
    private val control: ControlSimulation,
    private val sequences: Sequences,
    private val clock: SimulationClock,
    private val settings: SpaceSettings,
) : ManageSpaces {
    override val idleLifetime: Duration get() = settings.idleLifetime

    override fun open(): Space? {
        val now = clock.now()
        return Space(SpaceId.random(), now, now).takeIf { registry.open(it, settings.capacity) }
    }

    override fun enter(id: SpaceId): Space? = if (id.isDefault) null else registry.touch(id, clock.now())

    override fun find(id: SpaceId): Space? = registry.find(id)

    override fun close(id: SpaceId): Boolean {
        if (id.isDefault || !registry.remove(id)) return false
        control.reset(id)
        sequences.clear(id)
        return true
    }

    override fun expireIdle(): List<SpaceId> {
        val horizon = clock.now().minus(settings.idleLifetime)
        control.purge(SpaceId.DEFAULT, horizon)
        return registry.idleSince(horizon).filter(::close)
    }
}
