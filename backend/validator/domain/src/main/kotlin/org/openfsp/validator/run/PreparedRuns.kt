package org.openfsp.validator.run

import org.openfsp.ddd.DomainService
import org.openfsp.validator.problem.ProblemKind
import org.openfsp.validator.problem.ValidatorException
import org.openfsp.validator.suite.TargetKind
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PreparedRun(
    val id: UUID,
    val target: TargetKind,
    val preparedAt: Instant,
    val startBefore: Instant,
)

@DomainService
class PreparedRuns(
    private val settings: RunSettings,
    private val sessions: RunSessions,
    private val clock: Clock,
) {
    private val runs = ConcurrentHashMap<UUID, PreparedRun>()
    private val started: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    @Synchronized
    fun prepare(target: TargetKind): PreparedRun {
        if (runs.keys.count { it !in started } >= settings.maxPreparedRuns) {
            throw ValidatorException(ProblemKind.BUSY, "This validator holds ${settings.maxPreparedRuns} prepared runs. Start or let one expire, or try again in a few minutes.")
        }
        val now = clock.instant()
        val run = PreparedRun(UUID.randomUUID(), target, now, now.plus(settings.preparedRunLifetime))
        sessions.open(run.id)
        runs[run.id] = run
        return run
    }

    fun find(id: UUID): PreparedRun? = runs[id]

    fun isStarted(id: UUID): Boolean = id in started

    fun isWaiting(id: UUID): Boolean = runs[id] != null && id !in started

    fun <T> starting(
        id: UUID,
        start: (PreparedRun) -> T,
    ): T? {
        val run = runs[id]?.takeIf { clock.instant().isBefore(it.startBefore) } ?: return null
        synchronized(run) {
            if (id in started) return null
            return start(run).also { started += id }
        }
    }

    fun forgetExpired() {
        val now = clock.instant()
        runs.values.filter { it.id !in started && !now.isBefore(it.startBefore) }.forEach { run ->
            if (runs.remove(run.id, run)) {
                sessions.find(run.id)?.finish(now)
                sessions.forget(run.id)
            }
        }
        runs.keys.filter { it in started && sessions.find(it) == null }.forEach { id ->
            runs.remove(id)
            started.remove(id)
        }
    }
}
