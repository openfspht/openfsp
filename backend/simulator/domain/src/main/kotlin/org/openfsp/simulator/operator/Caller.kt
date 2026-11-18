package org.openfsp.simulator.operator

data class Caller(
    val authenticated: Boolean,
    val credential: String,
    val principal: Principal = Principal.PRIMARY,
    val account: String = Accounts.PUBLISHED,
)
