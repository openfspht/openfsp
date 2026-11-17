package org.openfsp.simulator.operator

import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.scenario.Scenario

data class OperatorProfile(
    val id: String,
    val dialect: Dialect,
    val referencePrefix: String,
    val lookup: Boolean,
    val callbacks: Boolean,
    val idempotentByOrder: Boolean,
    val statements: Boolean,
    val returnUrl: String?,
) {
    init {
        require(id.startsWith(RESERVED_PREFIX)) { "simulated operators use the reserved $RESERVED_PREFIX prefix" }
    }

    val scenarios: Set<Scenario>
        get() = dialect.scenarios - (if (callbacks) emptySet() else NEEDS_CALLBACKS) - (if (lookup) emptySet() else NEEDS_LOOKUP)

    val rotation: List<Scenario> get() = dialect.rotation.filter { it in scenarios }

    fun hostsCheckoutFor(payment: SimulatedPayment): Boolean = dialect != Dialect.OPENFSP || payment.msisdn == null

    companion object {
        const val RESERVED_PREFIX = "mock_"
        private val NEEDS_CALLBACKS = setOf(Scenario.DUPLICATE_CALLBACK, Scenario.LATE_CALLBACK)
        private val NEEDS_LOOKUP = setOf(Scenario.UNKNOWN_STATUS, Scenario.CONTRADICT)
    }
}
