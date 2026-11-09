package org.openfsp.simulator

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("simulator")
data class SimulatorProperties(
    val responseDelay: Duration,
    val settleAfter: Duration,
    val hangDuration: Duration,
    val lateCallbackDelay: Duration,
    val operators: Map<String, OperatorProfile>,
) {
    init {
        require(operators.keys.all { it.startsWith("mock_") }) { "simulated operators use the reserved mock_ prefix" }
    }

    data class OperatorProfile(
        val referencePrefix: String,
        val apiKey: String,
        val callbackSecret: String? = null,
        val lookup: Boolean,
        val callbacks: Boolean,
        val idempotentByOrder: Boolean,
        val statements: Boolean = false,
    )
}
