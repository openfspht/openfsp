package org.openfsp.simulator.common

import org.openfsp.ddd.DomainService
import org.openfsp.protocol.StatementPeriod
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

@DomainService
class SimulationClock(
    private val clock: Clock,
) {
    fun now(): Instant = clock.instant()

    fun localNow(): LocalDateTime = LocalDateTime.ofInstant(now(), StatementPeriod.BUSINESS_ZONE)

    fun today(): LocalDate = localNow().toLocalDate()

    fun businessDate(instant: Instant): LocalDate = instant.atZone(StatementPeriod.BUSINESS_ZONE).toLocalDate()
}
