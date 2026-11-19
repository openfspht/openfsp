package org.openfsp.gateway.provider

import java.time.Instant

data class CredentialedProvider(
    val id: String,
    val displayName: String,
    val credentialNames: List<String>,
    val hasPlatformCredentials: Boolean,
    val lookupNotifications: Boolean,
) {
    val acceptsAccountCredentials: Boolean get() = credentialNames.isNotEmpty()
}

data class StoredCredentials(
    val provider: String,
    val names: List<String>,
    val updatedAt: Instant,
)

enum class CredentialOrigin(
    val wire: String,
) {
    ACCOUNT("account"),
    PLATFORM("platform"),
    NONE("none"),
}

data class AccountProvider(
    val provider: CredentialedProvider,
    val origin: CredentialOrigin,
    val updatedAt: Instant?,
) {
    val notifiesAccount: Boolean get() = provider.lookupNotifications && provider.acceptsAccountCredentials
}
