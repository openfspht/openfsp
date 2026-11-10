package org.openfsp.simulator.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("simulator")
data class SimulatorProperties(
    val responseDelay: Duration,
    val settleAfter: Duration,
    val hangDuration: Duration,
    val lateCallbackDelay: Duration,
    val operators: Map<String, Operator>,
    val publicBaseUrl: String = "http://127.0.0.1:8090",
) {
    data class Operator(
        val referencePrefix: String,
        val apiKey: String,
        val callbackSecret: String? = null,
        val lookup: Boolean,
        val callbacks: Boolean,
        val idempotentByOrder: Boolean,
        val statements: Boolean = false,
        val returnUrl: String? = null,
    )
}
