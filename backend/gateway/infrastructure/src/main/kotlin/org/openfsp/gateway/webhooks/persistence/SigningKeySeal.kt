package org.openfsp.gateway.webhooks.persistence

import org.openfsp.gateway.providers.credentials.CredentialCipher
import org.openfsp.gateway.providers.credentials.SealedCredentials
import org.openfsp.gateway.shared.security.ApplicationKey
import org.springframework.stereotype.Component

@Component
class SigningKeySeal(
    applicationKey: ApplicationKey,
) {
    private val cipher = CredentialCipher(applicationKey.derive(ApplicationKey.Purpose.WEBHOOK_SIGNING_KEYS))

    fun seal(
        kid: String,
        privateKey: ByteArray,
    ): SealedCredentials = cipher.seal(privateKey, context(kid))

    fun open(
        kid: String,
        sealed: SealedCredentials,
    ): ByteArray = cipher.open(sealed, context(kid))

    private fun context(kid: String) = "$CONTEXT/$kid"

    private companion object {
        const val CONTEXT = "webhook-signing-key"
    }
}
