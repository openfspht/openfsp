package org.openfsp.validator.web.configuration

import org.openfsp.validator.web.runs.HostLookup
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.InetAddress
import java.time.Clock
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Configuration(proxyBeanMethods = false)
class ValidatorConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean(destroyMethod = "shutdownNow")
    fun runExecutor(): ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    @Bean
    fun hostLookup(): HostLookup = HostLookup { host -> InetAddress.getAllByName(host).toList() }
}
