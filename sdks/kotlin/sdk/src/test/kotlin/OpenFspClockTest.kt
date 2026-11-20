package org.openfsp.sdk

import org.openfsp.sdk.http.OpenFspClock
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpenFspClockTest {
    @Test
    fun `turns an interrupted wait into a transport failure and keeps the interrupt`() {
        Thread.currentThread().interrupt()
        try {
            assertFailsWith<TransportException> { OpenFspClock.SYSTEM.sleep(Duration.ofSeconds(5)) }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }
}
