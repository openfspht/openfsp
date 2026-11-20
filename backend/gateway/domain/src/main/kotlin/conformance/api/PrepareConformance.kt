package org.openfsp.gateway.conformance.api

import org.openfsp.gateway.conformance.ConformanceFixtures
import java.net.URI

interface PrepareConformance {
    fun prepare(webhookUrl: URI?): ConformanceFixtures

    fun sweep()
}
