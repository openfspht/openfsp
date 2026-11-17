package org.openfsp.simulator

import org.openfsp.simulator.adapters.ScheduledTimers
import org.openfsp.simulator.configuration.ExecutionConfiguration
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ScheduledTimersTest {
    private val execution = ExecutionConfiguration()
    private val scheduler = execution.scheduler()
    private val workers = execution.timerWorkers()
    private val timers = ScheduledTimers(scheduler, workers)

    @AfterTest
    fun stop() {
        scheduler.shutdownNow()
        workers.shutdownNow()
    }

    @Test
    fun `a blocked task does not delay the tasks scheduled after it`() {
        val release = CountDownLatch(1)
        val second = CountDownLatch(1)
        timers.after(Duration.ZERO) { release.await() }
        timers.after(Duration.ofMillis(10)) { second.countDown() }
        try {
            assertTrue(second.await(2, TimeUnit.SECONDS))
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `a failing task does not stop the tasks scheduled after it`() {
        val next = CountDownLatch(1)
        timers.after(Duration.ZERO) { error("simulated failure") }
        timers.after(Duration.ofMillis(10)) { next.countDown() }
        assertTrue(next.await(2, TimeUnit.SECONDS))
    }
}
