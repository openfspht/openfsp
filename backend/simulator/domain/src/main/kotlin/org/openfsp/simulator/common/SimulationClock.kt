package org.openfsp.simulator.common

import org.openfsp.ddd.DomainService
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@DomainService
class SimulationClock(
    private val clock: Clock,
) {
    fun now(): Instant = clock.instant()

    fun localNow(): LocalDateTime = LocalDateTime.ofInstant(now(), ZONE)

    fun today(): LocalDate = localNow().toLocalDate()

    companion object {
        val ZONE: ZoneId = ZoneId.of("America/Port-au-Prince")
    }
}
