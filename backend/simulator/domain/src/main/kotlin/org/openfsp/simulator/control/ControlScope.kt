package org.openfsp.simulator.control

import org.openfsp.simulator.operator.Accounts

sealed interface ControlScope {
    val account: String

    fun sees(owner: String?): Boolean

    data object Instance : ControlScope {
        override val account: String = Accounts.PUBLISHED

        override fun sees(owner: String?): Boolean = true
    }

    data class MerchantAccount(
        override val account: String,
    ) : ControlScope {
        override fun sees(owner: String?): Boolean = owner == account
    }
}
