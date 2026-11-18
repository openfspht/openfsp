package org.openfsp.gateway.admin.session

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.protocol.ProblemType
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.Semaphore

@Component
class AdminSignInGate(
    properties: GatewayProperties,
) {
    private val verifications = Semaphore(properties.adminSignInConcurrency)

    fun <T> verify(check: () -> T): T {
        if (!verifications.tryAcquire()) throw OpenFspException(ProblemType.RATE_LIMITED, retryAfter = RETRY_AFTER)
        try {
            return check()
        } finally {
            verifications.release()
        }
    }

    private companion object {
        val RETRY_AFTER: Duration = Duration.ofSeconds(1)
    }
}
