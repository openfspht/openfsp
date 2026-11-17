package org.openfsp.simulator.adapters

import org.openfsp.protocol.ResourceIds
import org.openfsp.simulator.operator.settlement.spi.Timers
import org.openfsp.simulator.operator.spi.Delays
import org.openfsp.simulator.operator.spi.PaymentIds
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

@Component
class SleepingDelays : Delays {
    override fun pause(duration: Duration) {
        if (!duration.isZero) Thread.sleep(duration)
    }
}

@Component
class ResourcePaymentIds : PaymentIds {
    override fun opaque(): String = ResourceIds.generate("pay")
}
