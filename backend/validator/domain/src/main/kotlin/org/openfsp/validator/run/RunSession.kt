package org.openfsp.validator.run

import org.openfsp.validator.problem.ProblemKind
import org.openfsp.validator.run.spi.RunEventStream
import java.time.Instant
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class RunFailure(
    val kind: ProblemKind,
    val detail: String?,
)

class RunSession(
    val id: UUID,
) {
    private val lock = ReentrantLock()
    private val events = mutableListOf<RunEvent>()
    private val followers = mutableListOf<Follower>()

    @Volatile
    var finishedAt: Instant? = null
        private set

    @Volatile
    var failure: RunFailure? = null
        private set

    val finished: Boolean get() = finishedAt != null

    fun publish(
        name: String,
        payload: Any,
    ) {
        val state =
            lock.withLock {
                events += RunEvent(events.size + 1L, name, payload)
                state()
            }
        state.notifyLater()
    }

    fun finish(
        at: Instant,
        failure: RunFailure? = null,
    ) {
        val state =
            lock.withLock {
                this.failure = failure
                finishedAt = at
                state().also { followers.clear() }
            }
        state.notifyLater()
    }

    fun subscribe(
        lastEventId: Long?,
        stream: RunEventStream,
    ) {
        val follower = Follower(stream, lastEventId ?: 0)
        val state =
            lock.withLock {
                if (!finished) {
                    followers += follower
                    stream.onClose { lock.withLock { followers.remove(follower) } }
                }
                state()
            }
        follower.catchUp(state.events, state.finished)
    }

    private fun state() = State(events.toList(), finished, followers.toList())

    private inner class State(
        val events: List<RunEvent>,
        val finished: Boolean,
        val followers: List<Follower>,
    ) {
        fun notifyLater() = followers.forEach { follower -> Thread.ofVirtual().start { follower.catchUp(events, finished) } }
    }

    private inner class Follower(
        private val stream: RunEventStream,
        private var cursor: Long,
    ) {
        private val sending = ReentrantLock()
        private var done = false

        fun catchUp(
            events: List<RunEvent>,
            finished: Boolean,
        ) = sending.withLock {
            if (done) return@withLock
            val batch = events.filter { it.id > cursor }
            if (batch.isNotEmpty()) {
                if (!stream.send(batch)) return@withLock leave()
                cursor = batch.last().id
            }
            if (finished && cursor >= events.size) {
                leave()
                stream.complete()
            }
        }

        private fun leave() {
            done = true
            lock.withLock { followers.remove(this) }
        }
    }
}
