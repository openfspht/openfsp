package org.openfsp.gateway.shared.ratelimits

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.payment.spi.SynchronizationLimits
import org.openfsp.protocol.ProblemType
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

@Configuration(proxyBeanMethods = false)
class RateLimitConfiguration(
    private val clock: Clock,
) {
    @Bean
    fun clientRateLimiter(): RateLimiter = RateLimiter(CLIENT_PER_SECOND, CLIENT_BURST, clock)

    @Bean
    fun publicRateLimiter(): RateLimiter = RateLimiter(PUBLIC_PER_SECOND, PUBLIC_BURST, clock)

    @Bean
    fun synchronizationRateLimiter(): RateLimiter = RateLimiter(1.0 / SYNCHRONIZE_EVERY_SECONDS, SYNCHRONIZE_BURST, clock)

    @Bean
    fun transactionResolutionRateLimiter(): RateLimiter = RateLimiter(TRANSACTION_RESOLUTIONS_PER_SECOND, TRANSACTION_RESOLUTION_BURST, clock)

    @Bean
    fun adminSignInRateLimiter(): RateLimiter = RateLimiter(1.0 / ADMIN_SIGN_IN_EVERY_SECONDS, ADMIN_SIGN_IN_BURST, clock)

    private companion object {
        const val CLIENT_PER_SECOND = 100.0
        const val CLIENT_BURST = 200.0
        const val PUBLIC_PER_SECOND = 20.0
        const val PUBLIC_BURST = 40.0
        const val SYNCHRONIZE_EVERY_SECONDS = 2.0
        const val SYNCHRONIZE_BURST = 3.0
        const val TRANSACTION_RESOLUTIONS_PER_SECOND = 1.0
        const val TRANSACTION_RESOLUTION_BURST = 10.0
        const val ADMIN_SIGN_IN_EVERY_SECONDS = 3.0
        const val ADMIN_SIGN_IN_BURST = 5.0
    }
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
