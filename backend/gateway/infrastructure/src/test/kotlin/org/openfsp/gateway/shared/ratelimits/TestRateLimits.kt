package org.openfsp.gateway.shared.ratelimits

import java.time.Clock

fun rateLimits(
    properties: RateLimitProperties,
    clock: Clock = Clock.systemUTC(),
): RateLimits =
    RateLimitConfiguration(properties, clock).run {
        RateLimits(clientRateLimiter(), publicRateLimiter(), synchronizationRateLimiter(), transactionResolutionRateLimiter(), adminSignInRateLimiter())
    }
