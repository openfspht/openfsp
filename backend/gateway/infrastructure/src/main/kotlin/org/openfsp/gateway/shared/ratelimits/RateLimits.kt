package org.openfsp.gateway.shared.ratelimits

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.payment.spi.SynchronizationLimits
import org.openfsp.protocol.ProblemType
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

@ConfigurationProperties("openfsp.rate-limits")
data class RateLimitProperties(
    val clientPerSecond: Double = 100.0,
    val clientBurst: Double = 200.0,
    val publicPerSecond: Double = 20.0,
    val publicBurst: Double = 40.0,
    val synchronizeEverySeconds: Double = 2.0,
    val synchronizeBurst: Double = 3.0,
    val transactionResolutionsPerSecond: Double = 1.0,
    val transactionResolutionBurst: Double = 10.0,
    val adminSignInEverySeconds: Double = 3.0,
    val adminSignInBurst: Double = 5.0,
)

@Configuration(proxyBeanMethods = false)
class RateLimitConfiguration(
    private val properties: RateLimitProperties,
    private val clock: Clock,
) {
    @Bean
    fun clientRateLimiter(): RateLimiter = RateLimiter(properties.clientPerSecond, properties.clientBurst, clock)

    @Bean
    fun publicRateLimiter(): RateLimiter = RateLimiter(properties.publicPerSecond, properties.publicBurst, clock)

    @Bean
    fun synchronizationRateLimiter(): RateLimiter = RateLimiter(1.0 / properties.synchronizeEverySeconds, properties.synchronizeBurst, clock)

    @Bean
    fun transactionResolutionRateLimiter(): RateLimiter = RateLimiter(properties.transactionResolutionsPerSecond, properties.transactionResolutionBurst, clock)

    @Bean
    fun adminSignInRateLimiter(): RateLimiter = RateLimiter(1.0 / properties.adminSignInEverySeconds, properties.adminSignInBurst, clock)
}

@Component
class RateLimits(
    @param:Qualifier("clientRateLimiter") private val clients: RateLimiter,
    @param:Qualifier("publicRateLimiter") private val public: RateLimiter,
    @param:Qualifier("synchronizationRateLimiter") private val synchronizations: RateLimiter,
    @param:Qualifier("transactionResolutionRateLimiter") private val transactionResolutions: RateLimiter,
    @param:Qualifier("adminSignInRateLimiter") private val adminSignIns: RateLimiter,
) : SynchronizationLimits {

    fun client(principalId: String) = check(clients, principalId)

    fun anonymous(address: String) = check(public, address)

    override fun tryAcquire(paymentId: String): Duration? = synchronizations.acquire(paymentId)

    override fun tryResolve(addressee: String): Duration? = transactionResolutions.acquire(addressee)

    fun adminSignIn(address: String) = check(adminSignIns, address)

    private fun check(
        limiter: RateLimiter,
        key: String,
    ) {
        val wait = limiter.acquire(key) ?: return
        throw OpenFspException(ProblemType.RATE_LIMITED, retryAfter = wait)
    }
}
