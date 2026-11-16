package org.openfsp.gateway.auth.spi

import org.openfsp.gateway.auth.ApiKey
import java.time.Instant

interface ApiKeys {
    fun withPrefix(lookupPrefix: String): List<ApiKey>

    fun withDigest(digest: String): ApiKey?

    fun principalNamed(
        name: String,
        at: Instant,
    ): String

    fun save(key: ApiKey)

    fun touch(
        keyId: String,
        at: Instant,
    )

    fun revoke(
        keyId: String,
        at: Instant,
    ): ApiKey?

    fun count(): Long
}
