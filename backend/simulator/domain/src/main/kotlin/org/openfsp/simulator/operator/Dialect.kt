package org.openfsp.simulator.operator

import org.openfsp.simulator.scenario.Scenario

enum class Dialect(
    val scenarios: Set<Scenario>,
) {
    OPENFSP(Scenario.entries.toSet()),
    MONCASH(NETWORK_FAILURES + setOf(Scenario.SUCCESS, Scenario.PENDING_FOREVER, Scenario.EXPIRE)),
    NATCASH(
        NETWORK_FAILURES +
            setOf(
                Scenario.SUCCESS,
                Scenario.DECLINE,
                Scenario.PENDING_FOREVER,
                Scenario.EXPIRE,
                Scenario.NO_CALLBACK,
                Scenario.DUPLICATE_CALLBACK,
                Scenario.LATE_CALLBACK,
            ),
    ),
    ;

    val rotation: List<Scenario> get() = Scenario.entries.filter { it in scenarios && it !in TESTS_ONLY }
}

private val TESTS_ONLY: Set<Scenario>
    get() = setOf(Scenario.CREDENTIAL_ECHO)

private val NETWORK_FAILURES: Set<Scenario>
    get() = setOf(Scenario.TIMEOUT, Scenario.TIMEOUT_THEN_SUCCESS, Scenario.TIMEOUT_THEN_NOTHING, Scenario.PROVIDER_ERROR)
