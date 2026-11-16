package org.openfsp.gateway.shared.ratelimits

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.protocol.ProblemType
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Clock

@ConfigurationProperties("openfsp.rate-limits")
data class RateLimitProperties(
    val clientPerSecond: Double = 100.0,
    val clientBurst: Double = 200.0,
    val publicPerSecond: Double = 20.0,
    val publicBurst: Double = 40.0,
    val synchronizeEverySeconds: Double = 2.0,
    val synchronizeBurst: Double = 3.0,
    val adminSignInEverySeconds: Double = 3.0,
    val adminSignInBurst: Double = 5.0,
)

@Component
class RateLimits(
    properties: RateLimitProperties,
    clock: Clock,
) {
    private val clients = RateLimiter(properties.clientPerSecond, properties.clientBurst, clock)
    private val public = RateLimiter(properties.publicPerSecond, properties.publicBurst, clock)
    private val synchronizations = RateLimiter(1.0 / properties.synchronizeEverySeconds, properties.synchronizeBurst, clock)
    private val adminSignIns = RateLimiter(1.0 / properties.adminSignInEverySeconds, properties.adminSignInBurst, clock)

    fun client(principalId: String) = check(clients, principalId)

    fun anonymous(address: String) = check(public, address)

    fun synchronization(paymentId: String) = check(synchronizations, paymentId)

    fun adminSignIn(address: String) = check(adminSignIns, address)

    private fun check(
        limiter: RateLimiter,
        key: String,
    ) {
        val wait = limiter.acquire(key) ?: return
        throw OpenFspException(ProblemType.RATE_LIMITED, retryAfter = wait)
    }
}
