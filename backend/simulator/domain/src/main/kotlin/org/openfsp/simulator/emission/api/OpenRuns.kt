package org.openfsp.simulator.emission.api

import org.openfsp.simulator.emission.ConformanceRun
import java.time.Duration

interface OpenRuns {
    fun open(
        operator: String,
        credential: String,
        webhookUrl: String?,
        lifetime: Duration,
    ): ConformanceRun

    fun active(
        operator: String,
        credential: String,
    ): ConformanceRun?
}
