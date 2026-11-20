package org.openfsp.simulator.shared.configuration

import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.FeeBearer
import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

@ConfigurationProperties("openfsp.simulator")
data class SimulatorProperties(
    val responseDelay: Duration,
    val settleAfter: Duration,
    val hangDuration: Duration,
    val lateCallbackDelay: Duration,
    val operators: Map<String, Operator>,
    val publicBaseUrl: String,
    val httpCallbackHosts: Set<String> = emptySet(),
    val retention: Duration = Duration.ofHours(2),
    val callbacks: Callbacks = Callbacks(),
    val journalCapacity: Int = 1000,
    val conformance: Conformance = Conformance(),
    val ui: Ui = Ui(),
) {
    init {
        val addresses = mapOf("public-base-url" to publicBaseUrl) +
            operators.flatMap { (id, operator) -> listOfNotNull(operator.returnUrl?.let { "operators.$id.return-url" to it }, operator.webhookUrl?.let { "operators.$id.webhook-url" to it }) }
        addresses.forEach { (name, value) ->
            check(isHttpUrl(value)) { "openfsp.simulator.$name is not an http(s) URL: set OPENFSP_SIMULATOR_URL and OPENFSP_GATEWAY_URL outside the development profile" }
        }
    }

    private fun isHttpUrl(value: String): Boolean = runCatching { URI(value) }.getOrNull()?.let { it.scheme in setOf("http", "https") && !it.host.isNullOrEmpty() } == true

    data class Ui(
        val sessionLifetime: Duration = Duration.ofHours(8),
    )

    data class Conformance(
        val runLifetime: Duration = Duration.ofHours(1),
        val maxActiveRuns: Int = 50,
    )

    data class Callbacks(
        val connectTimeout: Duration = Duration.ofSeconds(3),
        val readTimeout: Duration = Duration.ofSeconds(7),
        val privateAddressesAllowed: Boolean = false,
    )

    data class Fee(
        val rateBasisPoints: Int,
        val bearer: FeeBearer,
    )

    data class Limits(
        val minAmount: Long,
        val maxAmount: Long,
        val amountStep: Long,
    )

    data class Operator(
        val dialect: Dialect = Dialect.OPENFSP,
        val referencePrefix: String,
        val lookup: Boolean,
        val callbacks: Boolean,
        val idempotentByReference: Boolean,
        val statements: Boolean = false,
        val returnUrl: String? = null,
        val webhookUrl: String? = null,
        val displayName: String? = null,
        val credentials: Map<String, String> = emptyMap(),
        val fee: Fee? = null,
        val emitsEvents: Boolean = false,
        val limits: Limits? = null,
    ) {
        fun credential(name: String): String = credentialOrNull(name) ?: error("credential $name is missing")

        fun credentialOrNull(name: String): String? = credentials.entries.firstOrNull { normalized(it.key) == normalized(name) }?.value

        private fun normalized(name: String) = name.lowercase().filter { it.isLetterOrDigit() }
    }

    fun operator(id: String): Operator = operators[id] ?: error("No simulated operator named $id")
}
