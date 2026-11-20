package org.openfsp.gateway.auth.api

import org.openfsp.gateway.auth.IssuedKey
import org.openfsp.gateway.auth.Scope
import java.time.Instant

interface ManageKeys {
    fun issue(
        account: String,
        principal: String,
        scopes: Set<Scope>,
        expiresAt: Instant? = null,
    ): IssuedKey

    fun register(
        account: String,
        principal: String,
        secret: String,
        scopes: Set<Scope>,
    ): String?

    fun revoke(keyId: String): Boolean

    fun find(secret: String): String?

    fun isEmpty(): Boolean
}
