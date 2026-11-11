package org.openfsp.validator.web.runs

import org.openfsp.validator.web.configuration.ValidatorProperties
import org.openfsp.validator.web.problems.ProblemKind
import org.openfsp.validator.web.problems.ValidatorException
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Component
class RunRateLimiter(
    private val properties: ValidatorProperties,
    private val clock: Clock,
) {
    private val starts = HashMap<String, ArrayDeque<Instant>>()

    @Synchronized
    fun acquire(address: String) {
        val now = clock.instant()
        val horizon = now.minus(properties.runsWindow)
        if (starts.size > MAX_TRACKED) starts.values.removeIf { recent -> recent.all { !it.isAfter(horizon) } }
        val recent = starts.getOrPut(address) { ArrayDeque() }
        while (recent.firstOrNull()?.isAfter(horizon) == false) recent.removeFirst()
        if (recent.size >= properties.runsPerAddress) {
            val wait = Duration.between(now, recent.first().plus(properties.runsWindow))
            throw ValidatorException(ProblemKind.RATE_LIMITED, "At most ${properties.runsPerAddress} runs per ${properties.runsWindow.toMinutes()} minutes from one address.", retryAfter = wait)
        }
        recent.addLast(now)
    }

    private companion object {
        const val MAX_TRACKED = 10_000
    }
}
