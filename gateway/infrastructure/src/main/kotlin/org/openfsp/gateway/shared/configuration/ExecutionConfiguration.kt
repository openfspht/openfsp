package org.openfsp.gateway.shared.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Configuration(proxyBeanMethods = false)
class ExecutionConfiguration {
    @Bean(destroyMethod = "shutdown")
    fun workers(): ExecutorService = Executors.newVirtualThreadPerTaskExecutor()
}
