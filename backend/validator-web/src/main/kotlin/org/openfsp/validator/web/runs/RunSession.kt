package org.openfsp.validator.web.runs

import org.openfsp.validator.web.problems.ProblemBody
import org.springframework.http.MediaType
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class RunSession(
    val id: UUID,
    private val eventTimeout: Duration,
) {
    private val lock = ReentrantLock()
    private val events = mutableListOf<RunEvent>()
    private val emitters = mutableListOf<SseEmitter>()

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
        emitters.toList().forEach { emitter -> deliver(emitter, listOf(event)) }
    }

    fun finish(
        at: Instant,
        failure: ProblemBody? = null,
    ) = lock.withLock {
        this.failure = failure
        finishedAt = at
        emitters.toList().forEach(SseEmitter::complete)
        emitters.clear()
    }

    fun subscribe(lastEventId: Long?): SseEmitter =
        lock.withLock {
            val emitter = SseEmitter(eventTimeout.toMillis())
            val pending = events.filter { it.id > (lastEventId ?: 0) }
            if (!deliver(emitter, pending)) return emitter
            if (finished) {
                emitter.complete()
            } else {
                emitters += emitter
                val forget = Runnable { lock.withLock { emitters.remove(emitter) } }
                emitter.onCompletion(forget)
                emitter.onTimeout(forget)
                emitter.onError { forget.run() }
            }
            emitter
        }

    private fun deliver(
        emitter: SseEmitter,
        batch: List<RunEvent>,
    ): Boolean =
        try {
            batch.forEach { event -> emitter.send(SseEmitter.event().id(event.id.toString()).name(event.name).data(event.data, MediaType.TEXT_PLAIN)) }
            true
        } catch (_: IOException) {
            emitters.remove(emitter)
            emitter.complete()
            false
        } catch (_: IllegalStateException) {
            emitters.remove(emitter)
            false
        }
}
