package org.openfsp.simulator.emission.api

import org.openfsp.simulator.emission.ConformanceRun
import org.openfsp.simulator.emission.RunTerms
import org.openfsp.simulator.operator.Caller

interface OpenRuns {
    fun open(
        operator: String,
        credential: String,
        restrictedCredential: String,
        webhookUrl: String?,
        terms: RunTerms,
    ): ConformanceRun?

    fun authenticate(
        operator: String,
        credential: String,
    ): Caller?
}
