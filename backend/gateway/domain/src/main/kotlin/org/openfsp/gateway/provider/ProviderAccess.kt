package org.openfsp.gateway.provider

import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.provider.spi.Providers

enum class CredentialSource(
    val wire: String,
) {
    PLATFORM("platform"),
    ACCOUNT("account"),
    ;

    companion object {
        fun fromWire(value: String): CredentialSource = entries.first { it.wire == value }
    }
}

data class ProviderAccess(
    val credentials: CredentialSource,
    val operations: ProviderOperations,
)

data class ServedProvider(
    val id: String,
    val descriptor: ProviderDescriptor,
    val limits: AmountLimits,
)

fun Providers.servedOrNull(id: String): ServedProvider? = served().firstOrNull { it.id == id }

fun Providers.resolve(
    account: String,
    id: String,
): ProviderAccess? =
    ofAccount(account, id)?.let { ProviderAccess(CredentialSource.ACCOUNT, it) }
        ?: platform(id)?.let { ProviderAccess(CredentialSource.PLATFORM, it) }

fun Providers.addressed(
    id: String,
    account: String?,
): ProviderOperations? = if (account == null) platform(id) else ofAccount(account, id)

fun Providers.boundTo(payment: Payment): ProviderOperations? =
    when (payment.credentials) {
        CredentialSource.PLATFORM -> platform(payment.provider)
        CredentialSource.ACCOUNT -> ofAccount(payment.accountId, payment.provider)
    }

fun Payment.isAddressedBy(account: String?): Boolean =
    if (account == null) credentials == CredentialSource.PLATFORM else credentials == CredentialSource.ACCOUNT && accountId == account
