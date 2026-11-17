package org.openfsp.gateway.auth.api

import org.openfsp.gateway.auth.AuthenticatedKey

interface AuthenticateKey {
    fun authenticate(presented: String): AuthenticatedKey?
}
