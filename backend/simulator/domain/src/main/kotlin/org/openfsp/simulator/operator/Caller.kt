package org.openfsp.simulator.operator

data class Caller(
    val authenticated: Boolean,
    val credential: String,
    val principal: Principal,
    val account: String,
) {
    companion object {
        fun ofPublishedAccount(
            authenticated: Boolean,
            credential: String,
            principal: Principal = Principal.PRIMARY,
        ): Caller = Caller(authenticated, credential, principal, Accounts.PUBLISHED)
    }
}
