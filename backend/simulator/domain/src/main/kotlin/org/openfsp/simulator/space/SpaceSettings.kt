package org.openfsp.simulator.space

import java.time.Duration

data class SpaceSettings(
    val idleLifetime: Duration,
    val capacity: Int,
)
