package org.openfsp.gateway.providers.credentials

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
class ProviderCredentialSeal(
    @param:Qualifier("providerCredentialCipher") private val cipher: CredentialCipher,
) {
    val keyId: String get() = cipher.keyId

    fun seal(
        account: String,
        provider: String,
        revision: String,
        plaintext: ByteArray,
    ): SealedCredentials = cipher.seal(plaintext, context(account, provider, revision))

    fun open(
        account: String,
        provider: String,
        revision: String,
        sealed: SealedCredentials,
    ): ByteArray = cipher.open(sealed, context(account, provider, revision))

    fun openUnboundToRevision(
        account: String,
        provider: String,
        sealed: SealedCredentials,
    ): ByteArray = cipher.open(sealed, listOf(CONTEXT, account, provider).joinToString(SEPARATOR))

    private fun context(
        account: String,
        provider: String,
        revision: String,
    ) = listOf(CONTEXT, account, provider, revision).joinToString(SEPARATOR)

    private companion object {
        const val CONTEXT = "openfsp-provider-credentials"
        const val SEPARATOR = "\u0000"
    }
}
