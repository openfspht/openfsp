package org.openfsp.gateway.common

import org.openfsp.ddd.DomainService
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@DomainService
class BusinessCalendar(
    private val clock: Clock,
) {
    val zone: ZoneId = ZoneId.of("America/Port-au-Prince")

    fun now(): Instant = clock.instant().truncatedTo(ChronoUnit.MILLIS)

    fun today(): LocalDate = businessDate(clock.instant())

    fun businessDate(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    fun startOf(day: LocalDate): Instant = day.atStartOfDay(zone).toInstant()

    fun endOf(day: LocalDate): Instant = startOf(day.plusDays(1))

    fun fromLocal(local: LocalDateTime): Instant = local.atZone(zone).withLaterOffsetAtOverlap().toInstant()
}
