package org.openfsp.validator.run

import org.openfsp.ddd.DomainService
import java.time.Clock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@DomainService
class RunSessions(
    private val settings: RunSettings,
    private val clock: Clock,
) {
    private val sessions = ConcurrentHashMap<UUID, RunSession>()

    fun open(id: UUID): RunSession = RunSession(id).also { sessions[id] = it }

    fun transient(id: UUID): RunSession = RunSession(id)

    fun find(id: UUID): RunSession? = sessions[id]

    fun forget(id: UUID) {
        sessions.remove(id)
    }

    fun forgetFinished() {
        val horizon = clock.instant().minus(settings.finishedRunMemory)
        sessions.values.removeIf { session -> session.finishedAt?.isBefore(horizon) == true }
    }
}
