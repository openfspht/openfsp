package org.openfsp.gateway.webhooks

import org.openfsp.gateway.webhook.api.RotateSigningKeys
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SigningKeyRotationJob(
    private val rotation: RotateSigningKeys,
) {
    @Scheduled(fixedDelayString = "\${openfsp.webhooks.rotation-check-interval:1h}", initialDelayString = "\${openfsp.webhooks.rotation-check-interval:1h}")
    fun run() {
        rotation.current()
        rotation.rotateIfDue()
    }
}
