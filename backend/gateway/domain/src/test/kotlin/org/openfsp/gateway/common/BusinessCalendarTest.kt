package org.openfsp.gateway.common

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class BusinessCalendarTest {
    private val lateEvening = Instant.parse("2026-11-11T02:30:00Z")

    @Test
    fun `the business day follows Haiti time by default`() {
        val calendar = BusinessCalendar(Clock.fixed(lateEvening, ZoneOffset.UTC))
        assertEquals(LocalDate.of(2026, 11, 10), calendar.today())
    }

    @Test
    fun `the business day follows the configured time zone`() {
        val calendar = BusinessCalendar(Clock.fixed(lateEvening, ZoneOffset.UTC), ZoneId.of("Europe/Paris"))
        assertEquals(LocalDate.of(2026, 11, 11), calendar.today())
        assertEquals(Instant.parse("2026-11-10T23:00:00Z"), calendar.startOf(LocalDate.of(2026, 11, 11)))
    }
}
