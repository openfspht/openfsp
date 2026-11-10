package org.openfsp.simulator.dialects.moncash

import org.openfsp.simulator.operator.SimulationClock
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class MonCashTokens(
    private val clock: SimulationClock,
) {
    private val issued = ConcurrentHashMap<String, Pair<String, Instant>>()

    fun issue(operator: String): String {
        val token = UUID.randomUUID().toString()
        issued[token] = operator to clock.now().plus(LIFETIME)
        return token
    }

    fun isValid(
        operator: String,
        authorization: String?,
    ): Boolean {
        val (owner, expiresAt) = issued[authorization?.removePrefix(BEARER) ?: return false] ?: return false
        return owner == operator && clock.now().isBefore(expiresAt)
    }

    companion object {
        val LIFETIME: Duration = Duration.ofSeconds(59)
        private const val BEARER = "Bearer "
    }
}
