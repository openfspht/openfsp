package org.openfsp.gateway.auth.api

import org.openfsp.gateway.auth.AuthenticatedKey
import org.openfsp.gateway.auth.IssuedKey
import org.openfsp.gateway.auth.Scope
import java.time.Instant

interface AuthenticateKey {
    fun authenticate(presented: String): AuthenticatedKey?
}

interface ManageKeys {
    fun issue(
        principal: String,
        scopes: Set<Scope>,
        expiresAt: Instant? = null,
    ): IssuedKey

    fun register(
        principal: String,
        secret: String,
        scopes: Set<Scope>,
    ): String?

    fun revoke(keyId: String): Boolean

    fun isEmpty(): Boolean
}
