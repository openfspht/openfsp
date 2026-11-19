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

enum class CredentialStatus(
    val wire: String,
) {
    ACTIVE("active"),
    RETIRED("retired"),
    REVOKED("revoked"),
    ;

    companion object {
        fun fromWire(value: String): CredentialStatus = entries.first { it.wire == value }
    }
}

data class CredentialRevision(
    val id: String,
    val account: String,
    val provider: String,
    val names: List<String>,
    val status: CredentialStatus,
    val createdAt: Instant,
    val retiredAt: Instant? = null,
    val revokedAt: Instant? = null,
    val sealed: Boolean = status != CredentialStatus.REVOKED,
) {
    init {
        require((status == CredentialStatus.ACTIVE) == (retiredAt == null && revokedAt == null)) { "only an active revision is neither retired nor revoked" }
        require((status == CredentialStatus.REVOKED) == (revokedAt != null)) { "revoked_at goes with revoked only" }
        require(sealed || status != CredentialStatus.ACTIVE) { "an active revision holds its sealed credentials" }
        require(!sealed || status != CredentialStatus.REVOKED) { "a revoked revision no longer holds its credentials" }
    }
}

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
    val revisions: List<CredentialRevision> = emptyList(),
) {
    val active: CredentialRevision? get() = revisions.firstOrNull { it.status == CredentialStatus.ACTIVE }

    val updatedAt: Instant? get() = active?.createdAt

    val notifiesAccount: Boolean get() = provider.lookupNotifications && provider.acceptsAccountCredentials
}
