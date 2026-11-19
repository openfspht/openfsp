package org.openfsp.gateway.webhooks.persistence

import org.openfsp.gateway.providers.credentials.CredentialCipher
import org.openfsp.gateway.providers.credentials.SealedCredentials
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
class SigningKeySeal(
    @param:Qualifier("webhookSigningKeyCipher") private val cipher: CredentialCipher,
) {

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
