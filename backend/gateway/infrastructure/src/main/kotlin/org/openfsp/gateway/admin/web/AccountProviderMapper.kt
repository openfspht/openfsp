package org.openfsp.gateway.admin.web

import org.openfsp.gateway.provider.AccountProvider
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.springframework.stereotype.Component
import java.net.URI

@Component
class AccountProviderMapper(
    private val gateway: GatewayProperties,
) {
    fun toCollection(
        account: String,
        providers: List<AccountProvider>,
    ): AccountProviderCollection = AccountProviderCollection(providers.map { toResource(account, it) })

    fun toResource(
        account: String,
        provider: AccountProvider,
    ): AccountProviderResource {
        val id = provider.provider.id
        return AccountProviderResource(
            provider = id,
            displayName = provider.provider.displayName,
            source = provider.origin.wire,
            acceptsAccountCredentials = provider.provider.acceptsAccountCredentials,
            credentialNames = provider.provider.credentialNames,
            updatedAt = provider.updatedAt,
            notificationUrl = if (provider.notifiesAccount) addressOf(CALLBACKS, id, account) else null,
            returnUrl = if (provider.notifiesAccount) addressOf(RETURN, id, account) else null,
        )
    }

    private fun addressOf(
        kind: String,
        provider: String,
        account: String,
    ) = URI("${gateway.publicBaseUrl.toString().trimEnd('/')}/$kind/$provider/accounts/$account")

    private companion object {
        const val CALLBACKS = "callbacks"
        const val RETURN = "return"
    }
}
