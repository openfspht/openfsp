package org.openfsp.simulator.common

import org.openfsp.ddd.DomainService
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@DomainService
class SimulationClock(
    private val clock: Clock,
) {
    fun now(): Instant = clock.instant()

    fun localNow(): LocalDateTime = LocalDateTime.ofInstant(now(), HAITI)

    fun today(): LocalDate = localNow().toLocalDate()

    fun localStamp(): String = LOCAL.format(localNow())

    private companion object {
        val HAITI: ZoneId = ZoneId.of("America/Port-au-Prince")
        val LOCAL: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }
}
