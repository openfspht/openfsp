package org.openfsp.gateway.webhooks

import org.openfsp.gateway.webhook.api.RotateSigningKeys
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class SigningKeyRotationJob(
    private val rotation: RotateSigningKeys,
) {
    @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.HOURS)
    fun run() {
        rotation.current()
        rotation.rotateIfDue()
    }
}
