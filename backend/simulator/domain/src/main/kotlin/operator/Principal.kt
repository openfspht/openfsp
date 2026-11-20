package org.openfsp.simulator.operator

enum class Principal(
    val readsStatements: Boolean,
) {
    PRIMARY(readsStatements = true),
    SECONDARY(readsStatements = false),
}
