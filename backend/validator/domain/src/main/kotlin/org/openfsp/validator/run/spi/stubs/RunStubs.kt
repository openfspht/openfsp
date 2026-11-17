package org.openfsp.validator.run.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.validator.engine.Exchange
import org.openfsp.validator.engine.OutgoingRequest
import org.openfsp.validator.engine.spi.Transport
import org.openfsp.validator.problem.ProblemKind
import org.openfsp.validator.problem.ValidatorException
import org.openfsp.validator.run.RunEvent
import org.openfsp.validator.run.spi.RunAlerts
import org.openfsp.validator.run.spi.RunEventStream
import org.openfsp.validator.run.spi.SimulatorSpace
import org.openfsp.validator.run.spi.SimulatorSpaces
import org.openfsp.validator.run.spi.TargetConnection
import org.openfsp.validator.run.spi.TargetConnections
import java.net.URI
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@Stub
class InMemorySimulatorSpaces(
    private val base: String = "http://simulator.test",
) : SimulatorSpaces {
    val opened = CopyOnWriteArrayList<SimulatorSpace>()
    val closed = CopyOnWriteArrayList<SimulatorSpace>()
    private val sequence = AtomicInteger()

    @Volatile
    var unavailable = false

    override fun open(): SimulatorSpace {
        if (unavailable) throw ValidatorException(ProblemKind.SIMULATOR_UNAVAILABLE)
        val id = "%032x".format(sequence.incrementAndGet())
        return SimulatorSpace(id, "$base/runs/$id", "$base/runs/$id").also { opened += it }
    }

    override fun close(space: SimulatorSpace) {
        closed += space
    }
}

@Stub
class DirectTargetConnections(
    private val transport: Transport,
) : TargetConnections {
    val opened = CopyOnWriteArrayList<URI>()
    val closed = AtomicInteger()

    override fun open(
        baseUrl: URI,
        timeout: Duration,
    ): TargetConnection {
        opened += baseUrl
        return object : TargetConnection {
            override fun send(request: OutgoingRequest): Exchange = transport.send(request)

            override fun close() {
                closed.incrementAndGet()
            }
        }
    }
}

@Stub
class RecordingRunAlerts : RunAlerts {
    val stopped = CopyOnWriteArrayList<UUID>()

    override fun stopped(
        id: UUID,
        cause: Exception,
    ) {
        stopped += id
    }
}

@Stub
class CollectingRunEventStream : RunEventStream {
    val events = CopyOnWriteArrayList<RunEvent>()

    @Volatile
    var completed = false

    override fun send(events: List<RunEvent>): Boolean {
        this.events += events
        return true
    }

    override fun complete() {
        completed = true
    }

    override fun onClose(action: () -> Unit) {}
}
