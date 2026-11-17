package org.openfsp.simulator.spaces

import org.openfsp.simulator.space.api.ManageSpaces
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SpaceExpiry(
    private val spaces: ManageSpaces,
) {
    @Scheduled(fixedDelayString = "\${simulator.spaces.sweep-interval:1m}")
    fun sweep() {
        spaces.expireIdle()
    }
}
