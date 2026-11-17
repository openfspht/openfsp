package org.openfsp.simulator.scenario

import org.openfsp.simulator.payment.Outcome

enum class Scenario {
    SUCCESS,
    DECLINE,
    INSUFFICIENT,
    TIMEOUT,
    TIMEOUT_THEN_SUCCESS,
    TIMEOUT_THEN_NOTHING,
    PENDING_FOREVER,
    EXPIRE,
    NO_CALLBACK,
    DUPLICATE_CALLBACK,
    LATE_CALLBACK,
    UNKNOWN_STATUS,
    PROVIDER_ERROR,
    CREDENTIAL_ECHO,
    CONTRADICT,
    AMOUNT_MISMATCH,
    ;

    val hangs: Boolean get() = this in setOf(TIMEOUT, TIMEOUT_THEN_SUCCESS, TIMEOUT_THEN_NOTHING)

    val settlesBy: Pair<Outcome, String?>?
        get() =
            when (this) {
                DECLINE -> Outcome.FAILED to "DECLINED"
                INSUFFICIENT -> Outcome.FAILED to "INSUFFICIENT_FUNDS"
                PENDING_FOREVER, EXPIRE, PROVIDER_ERROR, TIMEOUT_THEN_NOTHING, CREDENTIAL_ECHO -> null
                else -> Outcome.SUCCESSFUL to null
            }
}
