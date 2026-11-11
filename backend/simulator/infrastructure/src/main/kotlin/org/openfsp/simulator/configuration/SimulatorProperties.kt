package org.openfsp.simulator.configuration

import org.openfsp.simulator.operator.Dialect
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
    val httpCallbackHosts: Set<String> = emptySet(),
    val spaces: Spaces = Spaces(),
) {
    data class Spaces(
        val idleLifetime: Duration = Duration.ofHours(2),
        val capacity: Int = 256,
    )

    data class Operator(
        val dialect: Dialect = Dialect.OPENFSP,
        val referencePrefix: String,
        val lookup: Boolean,
        val callbacks: Boolean,
        val idempotentByOrder: Boolean,
        val statements: Boolean = false,
        val returnUrl: String? = null,
        val displayName: String? = null,
        val credentials: Map<String, String> = emptyMap(),
    ) {
        fun credential(name: String): String = credentialOrNull(name) ?: error("credential $name is missing")

        fun credentialOrNull(name: String): String? = credentials.entries.firstOrNull { normalized(it.key) == normalized(name) }?.value

        private fun normalized(name: String) = name.lowercase().filter { it.isLetterOrDigit() }
    }

    fun operator(id: String): Operator = operators[id] ?: error("No simulated operator named $id")
}
