package org.openfsp.gateway.account

sealed interface AccountFilter {
    fun admits(account: String): Boolean

    data object Every : AccountFilter {
        override fun admits(account: String): Boolean = true
    }

    data class Only(
        val id: String,
    ) : AccountFilter {
        override fun admits(account: String): Boolean = account == id
    }
}
