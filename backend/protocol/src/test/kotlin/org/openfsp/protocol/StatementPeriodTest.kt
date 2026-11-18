package org.openfsp.protocol

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StatementPeriodTest {
    private val day = LocalDate.parse("2026-09-28")

    @Test
    fun `a business date is a period of one day`() {
        assertEquals(StatementPeriod.Request.Accepted(StatementPeriod(day, day)), StatementPeriod.of("2026-09-28", null, null))
    }

    @Test
    fun `a range includes both ends and covers at most 31 days`() {
        assertEquals(StatementPeriod.Request.Accepted(StatementPeriod(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))), StatementPeriod.of(null, "2026-10-01", "2026-10-31"))
        assertEquals(StatementPeriod.TO, assertIs<StatementPeriod.Request.Invalid>(StatementPeriod.of(null, "2026-10-01", "2026-11-01")).parameter)
    }

    @Test
    fun `a request without a period is missing one`() {
        assertEquals(StatementPeriod.Request.Missing, StatementPeriod.of(null, null, null))
        assertEquals(StatementPeriod.Request.Missing, StatementPeriod.of(" ", "", null))
    }

    @Test
    fun `any other combination, an unreadable date or a reversed range is invalid`() {
        assertEquals(StatementPeriod.BUSINESS_DATE, assertIs<StatementPeriod.Request.Invalid>(StatementPeriod.of("2026-09-28", "2026-09-01", null)).parameter)
        assertEquals(StatementPeriod.TO, assertIs<StatementPeriod.Request.Invalid>(StatementPeriod.of(null, "2026-09-01", null)).parameter)
        assertEquals(StatementPeriod.FROM, assertIs<StatementPeriod.Request.Invalid>(StatementPeriod.of(null, null, "2026-09-01")).parameter)
        assertEquals(StatementPeriod.BUSINESS_DATE, assertIs<StatementPeriod.Request.Invalid>(StatementPeriod.of("28/09/2026", null, null)).parameter)
        assertEquals(StatementPeriod.FROM, assertIs<StatementPeriod.Request.Invalid>(StatementPeriod.of(null, "2026-02-30", "2026-03-01")).parameter)
        assertEquals(StatementPeriod.TO, assertIs<StatementPeriod.Request.Invalid>(StatementPeriod.of(null, "2026-09-02", "2026-09-01")).parameter)
    }
}
