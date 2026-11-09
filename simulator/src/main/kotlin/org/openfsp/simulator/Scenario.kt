package org.openfsp.simulator

enum class Scenario {
    SUCCESS,
    DECLINE,
    INSUFFICIENT,
    TIMEOUT,
    PENDING_FOREVER,
    EXPIRE,
    NO_CALLBACK,
    DUPLICATE_CALLBACK,
    PROVIDER_ERROR,
    AMOUNT_MISMATCH,
}

enum class Outcome(
    val wire: String,
) {
    SUCCESSFUL("SUCCESSFUL"),
    FAILED("FAILED"),
    EXPIRED("EXPIRED"),
}
