package org.openfsp.gateway.admin.session

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.protocol.ProblemType
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.Semaphore

@Component
class AdminSignInGate {
    private val verifications = Semaphore(CONCURRENCY)

    fun <T> verify(check: () -> T): T {
        if (!verifications.tryAcquire()) throw OpenFspException(ProblemType.RATE_LIMITED, retryAfter = RETRY_AFTER)
        try {
            return check()
        } finally {
            verifications.release()
        }
    }

    private companion object {
        const val CONCURRENCY = 2
        val RETRY_AFTER: Duration = Duration.ofSeconds(1)
    }
}
