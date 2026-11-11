package org.openfsp.validator.web.runs

import org.openfsp.validator.web.configuration.ValidatorProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class RunSessions(
    private val properties: ValidatorProperties,
    private val clock: Clock,
) {
    private val sessions = ConcurrentHashMap<UUID, RunSession>()

    fun open(id: UUID): RunSession = RunSession(id, properties.eventTimeout).also { sessions[id] = it }

    fun transient(id: UUID): RunSession = RunSession(id, properties.eventTimeout)

    fun find(id: UUID): RunSession? = sessions[id]

    fun forget(id: UUID) {
        sessions.remove(id)
    }

    @Scheduled(fixedDelayString = "\${openfsp.validator.session-sweep-interval:1m}")
    fun forgetFinished() {
        val horizon = clock.instant().minus(properties.finishedRunMemory)
        sessions.values.removeIf { session -> session.finishedAt?.isBefore(horizon) == true }
    }
}
