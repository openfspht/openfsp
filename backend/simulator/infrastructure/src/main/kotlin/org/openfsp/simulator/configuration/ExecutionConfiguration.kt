package org.openfsp.simulator.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Configuration(proxyBeanMethods = false)
class ExecutionConfiguration {
    @Bean(destroyMethod = "shutdownNow")
    fun scheduler(): ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("simulation-timer").factory())

    @Bean(destroyMethod = "shutdownNow")
    fun timerWorkers(): ExecutorService =
        ThreadPoolExecutor(MAX_WORKERS, MAX_WORKERS, 0, TimeUnit.MILLISECONDS, LinkedBlockingQueue(MAX_QUEUED), Thread.ofVirtual().name("simulation-worker-", 0).factory())

    private companion object {
        const val MAX_WORKERS = 64
        const val MAX_QUEUED = 10_000
    }
}
