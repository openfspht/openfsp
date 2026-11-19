package org.openfsp.gateway.provider

import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.provider.spi.Providers

sealed interface PaymentCredentials {
    data object Platform : PaymentCredentials

    data class Account(
        val revision: String,
    ) : PaymentCredentials
}

data class ProviderAccess(
    val credentials: PaymentCredentials,
    val operations: ProviderOperations,
)

data class RevisionOperations(
    val revision: String,
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
    when (modeOf(account, id)) {
        CredentialMode.PLATFORM -> platform(id)?.let { ProviderAccess(PaymentCredentials.Platform, it) }
        CredentialMode.OWN -> active(account, id)?.let { ProviderAccess(PaymentCredentials.Account(it.revision), it.operations) }
    }

fun Providers.addressed(
    id: String,
    account: String?,
): ProviderOperations? = if (account == null) platform(id) else active(account, id)?.operations

fun Providers.boundTo(payment: Payment): ProviderOperations? =
    when (val credentials = payment.credentials) {
        PaymentCredentials.Platform -> platform(payment.provider)
        is PaymentCredentials.Account -> ofRevision(payment.accountId, payment.provider, credentials.revision)
    }

fun Payment.isAddressedBy(account: String?): Boolean =
    if (account == null) credentials == PaymentCredentials.Platform else credentials is PaymentCredentials.Account && accountId == account
