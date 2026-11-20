package org.openfsp.simulator.operator

data class Caller(
    val authenticated: Boolean,
    val principal: Principal,
    val account: String,
) {
    companion object {
        fun ofPublishedAccount(
            authenticated: Boolean,
            principal: Principal = Principal.PRIMARY,
        ): Caller = Caller(authenticated, principal, Accounts.PUBLISHED)
    }
}
