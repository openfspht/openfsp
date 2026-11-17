package org.openfsp.validator.run

import org.openfsp.ddd.DomainService
import org.openfsp.validator.problem.ProblemKind
import org.openfsp.validator.problem.ValidatorException
import java.time.Clock
import java.time.Duration
import java.time.Instant

@DomainService
class RunRateLimiter(
    private val settings: RunSettings,
    private val clock: Clock,
) {
    private val starts = HashMap<String, ArrayDeque<Instant>>()

    @Synchronized
    fun acquire(address: String) {
        val now = clock.instant()
        val horizon = now.minus(settings.runsWindow)
        if (starts.size > MAX_TRACKED) starts.values.removeIf { recent -> recent.all { !it.isAfter(horizon) } }
        val recent = starts.getOrPut(address) { ArrayDeque() }
        while (recent.firstOrNull()?.isAfter(horizon) == false) recent.removeFirst()
        if (recent.size >= settings.runsPerAddress) {
            val wait = Duration.between(now, recent.first().plus(settings.runsWindow))
            throw ValidatorException(ProblemKind.RATE_LIMITED, "At most ${settings.runsPerAddress} runs per ${settings.runsWindow.toMinutes()} minutes from one address.", retryAfter = wait)
        }
        recent.addLast(now)
    }

    @Synchronized
    fun refund(address: String) {
        starts[address]?.removeLastOrNull()
    }

    private companion object {
        const val MAX_TRACKED = 10_000
    }
}
