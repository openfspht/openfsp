package org.openfsp.gateway.webhook.api

import org.openfsp.gateway.webhook.ProvidedSigningKey
import org.openfsp.gateway.webhook.PublishedSigningKey
import java.security.PrivateKey
import java.time.Instant

interface RotateSigningKeys {
    fun current(): String

    fun provisionAtStartup(provided: ProvidedSigningKey?): String

    fun privateKey(kid: String): PrivateKey

    fun published(): List<PublishedSigningKey>

    fun rotate(immediate: Boolean): String

    fun rotateIfDue(): String?

    fun nextRotationAt(): Instant?
}
