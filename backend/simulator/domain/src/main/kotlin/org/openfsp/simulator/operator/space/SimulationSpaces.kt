package org.openfsp.simulator.operator.space

import org.openfsp.ddd.DomainService
import org.openfsp.simulator.operator.SimulationClock
import org.openfsp.simulator.operator.control.api.ControlSimulation
import org.openfsp.simulator.operator.space.api.ManageSpaces
import org.openfsp.simulator.operator.space.spi.SpaceRegistry
import org.openfsp.simulator.operator.spi.Sequences
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

    override fun expireIdle(): List<SpaceId> = registry.idleSince(clock.now().minus(settings.idleLifetime)).filter(::close)
}
