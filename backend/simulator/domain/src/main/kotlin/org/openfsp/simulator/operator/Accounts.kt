package org.openfsp.simulator.operator

object Accounts {
    const val PUBLISHED = "published"

    fun scoped(
        account: String,
        reference: String,
    ): String = if (account == PUBLISHED) reference else "$account/$reference"
}
