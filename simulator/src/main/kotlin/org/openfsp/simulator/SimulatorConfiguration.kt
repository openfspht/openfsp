package org.openfsp.simulator

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.time.Clock
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

@Configuration(proxyBeanMethods = false)
class SimulatorConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean(destroyMethod = "shutdownNow")
    fun scheduler(): ScheduledExecutorService = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory())

    @Bean
    fun callbackClient(builder: RestClient.Builder): RestClient = builder.build()
}
