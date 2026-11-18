package org.openfsp.simulator.dialects.moncash

import org.openfsp.simulator.common.SimulationClock
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class MonCashTokens(
    private val clock: SimulationClock,
) {
    private data class Issued(
        val operator: String,
        val expiresAt: Instant,
    )

    private val issued = ConcurrentHashMap<String, Issued>()

    fun issue(operator: String): String {
        val now = clock.now()
        issued.values.removeIf { !now.isBefore(it.expiresAt) }
        val token = UUID.randomUUID().toString()
        issued[token] = Issued(operator, now.plus(LIFETIME))
        return token
    }

    fun isValid(
        operator: String,
        authorization: String?,
    ): Boolean {
        val token = issued[authorization?.removePrefix(BEARER) ?: return false] ?: return false
        return token.operator == operator && clock.now().isBefore(token.expiresAt)
    }

    companion object {
        val LIFETIME: Duration = Duration.ofSeconds(59)
        private const val BEARER = "Bearer "
    }
}
