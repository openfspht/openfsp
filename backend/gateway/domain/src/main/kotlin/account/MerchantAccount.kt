package org.openfsp.gateway.account

import java.time.Instant

data class MerchantAccount(
    val id: String,
    val name: String,
    val createdAt: Instant,
    val expiresAt: Instant? = null,
) {
    fun isActiveAt(instant: Instant): Boolean = expiresAt == null || expiresAt.isAfter(instant)

    companion object {
        const val DEFAULT = "acct_default"
        private val NAME = Regex("^[a-z0-9][a-z0-9-]{0,62}$")

        fun isValidName(name: String): Boolean = NAME.matches(name)
    }
}
