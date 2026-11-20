package org.openfsp.gateway.shared.ratelimits

import java.time.Clock

fun rateLimits(
    clients: RateLimiter? = null,
    public: RateLimiter? = null,
    clock: Clock = Clock.systemUTC(),
): RateLimits =
    RateLimitConfiguration(clock).run {
        RateLimits(clients ?: clientRateLimiter(), public ?: publicRateLimiter(), synchronizationRateLimiter(), transactionResolutionRateLimiter(), adminSignInRateLimiter())
    }
