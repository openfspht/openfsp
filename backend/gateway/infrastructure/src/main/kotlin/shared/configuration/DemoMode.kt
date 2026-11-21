package org.openfsp.gateway.shared.configuration

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class DemoMode(
    val active: Boolean,
) {
    @Autowired
    constructor(environment: Environment) : this(PROFILE in environment.activeProfiles)

    companion object {
        const val PROFILE = "demo"
    }
}
