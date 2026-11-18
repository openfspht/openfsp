package org.openfsp.simulator.emission.api

import org.openfsp.simulator.emission.ConformanceRun
import org.openfsp.simulator.operator.Caller
import java.time.Duration

interface OpenRuns {
    fun open(
        operator: String,
        credential: String,
        restrictedCredential: String,
        webhookUrl: String?,
        lifetime: Duration,
    ): ConformanceRun

    fun authenticate(
        operator: String,
        credential: String,
    ): Caller?
}
