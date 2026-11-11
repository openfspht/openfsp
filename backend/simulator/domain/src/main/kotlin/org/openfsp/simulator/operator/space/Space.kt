package org.openfsp.simulator.operator.space

import java.time.Duration
import java.time.Instant

data class Space(
    val id: SpaceId,
    val openedAt: Instant,
    val lastUsedAt: Instant,
) {
    fun expiresAt(idleLifetime: Duration): Instant = lastUsedAt.plus(idleLifetime)
}

data class SpaceSettings(
    val idleLifetime: Duration,
    val capacity: Int,
)
