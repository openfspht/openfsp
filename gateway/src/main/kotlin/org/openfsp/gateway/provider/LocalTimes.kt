package org.openfsp.gateway.provider

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object LocalTimes {
    val HAITI: ZoneId = ZoneId.of("America/Port-au-Prince")

    fun toInstant(local: LocalDateTime): Instant = local.atZone(HAITI).withLaterOffsetAtOverlap().toInstant()

    fun businessDate(instant: Instant): LocalDate = instant.atZone(HAITI).toLocalDate()

    fun startOf(day: LocalDate): Instant = day.atStartOfDay(HAITI).toInstant()
}
