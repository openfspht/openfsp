package org.openfsp.simulator.adapters

import org.openfsp.simulator.operator.spi.Delays
import org.openfsp.simulator.operator.spi.Timers
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Component
class ScheduledTimers(
    private val scheduler: ScheduledExecutorService,
) : Timers {
    override fun after(
        delay: Duration,
        task: () -> Unit,
    ) {
        scheduler.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS)
    }
}

@Component
class SleepingDelays : Delays {
    override fun pause(duration: Duration) {
        if (!duration.isZero) Thread.sleep(duration)
    }
}
