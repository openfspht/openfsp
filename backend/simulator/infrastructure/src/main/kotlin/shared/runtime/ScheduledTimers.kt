package org.openfsp.simulator.shared.runtime

import org.openfsp.simulator.settlement.spi.Timers
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Component
class ScheduledTimers(
    private val scheduler: ScheduledExecutorService,
    @param:Qualifier("timerWorkers") private val workers: ExecutorService,
) : Timers {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun after(
        delay: Duration,
        task: () -> Unit,
    ) {
        scheduler.schedule({ handOff(task) }, delay.toMillis(), TimeUnit.MILLISECONDS)
    }

    private fun handOff(task: () -> Unit) {
        try {
            workers.execute { runCatching(task).onFailure { log.warn("A simulation timer task failed", it) } }
        } catch (rejected: RejectedExecutionException) {
            log.warn("A simulation timer task was dropped: {}", rejected.message)
        }
    }
}
