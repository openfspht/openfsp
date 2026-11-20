package org.openfsp.protocol

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RetryAfterTest {
    private val now = Instant.parse("1994-11-06T08:49:07Z")

    @Test
    fun `delta seconds are read as a delay`() {
        assertEquals(Duration.ofSeconds(120), RetryAfter.parse("120", now))
        assertEquals(Duration.ZERO, RetryAfter.parse("0", now))
        assertEquals(Duration.ofSeconds(Long.MAX_VALUE), RetryAfter.parse("99999999999999999999999", now))
    }

    @Test
    fun `an HTTP date in each of the three RFC 9110 formats is read as the delay until it`() {
        assertEquals(Duration.ofSeconds(30), RetryAfter.parse("Sun, 06 Nov 1994 08:49:37 GMT", now))
        assertEquals(Duration.ofSeconds(30), RetryAfter.parse("Sunday, 06-Nov-94 08:49:37 GMT", now))
        assertEquals(Duration.ofSeconds(30), RetryAfter.parse("Sun Nov  6 08:49:37 1994", now))
    }

    @Test
    fun `a date in the past asks for no delay`() {
        assertEquals(Duration.ZERO, RetryAfter.parse("Sat, 05 Nov 1994 08:49:37 GMT", now))
    }

    @Test
    fun `anything else is ignored`() {
        listOf("", "soon", "-5", "+5", "1.5", "Sun, 06 Nov 1994 08:49:37 CET", "Mon, 06 Nov 1994 08:49:37 GMT").forEach { value ->
            assertNull(RetryAfter.parse(value, now), value)
        }
    }
}
