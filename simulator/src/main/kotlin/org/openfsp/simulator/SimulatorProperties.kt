package org.openfsp.simulator

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("simulator")
data class SimulatorProperties(
    val responseDelay: Duration,
    val settleAfter: Duration,
    val hangDuration: Duration,
    val alpha: Alpha,
) {
    data class Alpha(
        val apiKey: String,
        val callbackSecret: String,
    )
}
