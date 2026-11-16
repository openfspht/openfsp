package org.openfsp.validator.run

import org.openfsp.validator.problem.ProblemBody
import org.openfsp.validator.run.spi.RunEventStream
import java.time.Instant
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class RunSession(
    val id: UUID,
) {
    private val lock = ReentrantLock()
    private val events = mutableListOf<RunEvent>()
    private val streams = mutableListOf<RunEventStream>()

    @Volatile
    var finishedAt: Instant? = null
        private set

    @Volatile
    var failure: ProblemBody? = null
        private set

    val finished: Boolean get() = finishedAt != null

    fun publish(
        name: String,
        payload: Any,
    ) = lock.withLock {
        val event = RunEvent(events.size + 1L, name, RunEvents.json.writeValueAsString(payload))
        events += event
        streams.toList().forEach { stream -> deliver(stream, listOf(event)) }
    }

    fun finish(
        at: Instant,
        failure: ProblemBody? = null,
    ) = lock.withLock {
        this.failure = failure
        finishedAt = at
        streams.toList().forEach(RunEventStream::complete)
        streams.clear()
    }

    fun subscribe(
        lastEventId: Long?,
        stream: RunEventStream,
    ) = lock.withLock {
        val pending = events.filter { it.id > (lastEventId ?: 0) }
        if (!deliver(stream, pending)) return@withLock
        if (finished) {
            stream.complete()
        } else {
            streams += stream
            stream.onClose { lock.withLock { streams.remove(stream) } }
        }
    }

    private fun deliver(
        stream: RunEventStream,
        batch: List<RunEvent>,
    ): Boolean = stream.send(batch).also { delivered -> if (!delivered) streams.remove(stream) }
}
