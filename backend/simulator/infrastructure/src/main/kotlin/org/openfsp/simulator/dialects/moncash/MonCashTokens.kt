package org.openfsp.simulator.dialects.moncash

import org.openfsp.simulator.operator.SimulationClock
import org.openfsp.simulator.operator.space.SpaceId
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
        val space: SpaceId,
        val operator: String,
        val expiresAt: Instant,
    )

    private val issued = ConcurrentHashMap<String, Issued>()

    fun issue(
        space: SpaceId,
        operator: String,
    ): String {
        val now = clock.now()
        issued.values.removeIf { !now.isBefore(it.expiresAt) }
        val token = UUID.randomUUID().toString()
        issued[token] = Issued(space, operator, now.plus(LIFETIME))
        return token
    }

    fun isValid(
        space: SpaceId,
        operator: String,
        authorization: String?,
    ): Boolean {
        val token = issued[authorization?.removePrefix(BEARER) ?: return false] ?: return false
        return token.space == space && token.operator == operator && clock.now().isBefore(token.expiresAt)
    }

    companion object {
        val LIFETIME: Duration = Duration.ofSeconds(59)
        private const val BEARER = "Bearer "
    }
}
