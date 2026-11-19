package org.openfsp.gateway.admin.web

import org.openfsp.gateway.provider.AccountProvider
import org.openfsp.gateway.provider.CredentialOrigin
import org.openfsp.gateway.provider.CredentialedProvider
import org.openfsp.gateway.shared.configuration.GatewayProperties
import java.net.URI
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountProviderMapperTest {
    private val mapper = AccountProviderMapper(GatewayProperties(GatewayProperties.Environment.TEST, false, URI("https://gateway.example/"), "key"))

    @Test
    fun `a provider notifying per account carries the addresses of that account`() {
        val natCash = CredentialedProvider("natcash", "NatCash", listOf("username"), hasPlatformCredentials = false, lookupNotifications = true)
        val at = Instant.parse("2026-11-17T10:15:30Z")
        val resource = mapper.toResource("acct_shop", AccountProvider(natCash, CredentialOrigin.ACCOUNT, at))
        assertEquals(
            AccountProviderResource(
                "natcash",
                "NatCash",
                "account",
                true,
                listOf("username"),
                at,
                URI("https://gateway.example/callbacks/natcash/accounts/acct_shop"),
                URI("https://gateway.example/return/natcash/accounts/acct_shop"),
            ),
            resource,
        )
    }

    @Test
    fun `a provider without account credentials has no address of its own`() {
        val openFsp = CredentialedProvider("openfsp", "OpenFSP", emptyList(), hasPlatformCredentials = true, lookupNotifications = true)
        val resource = mapper.toCollection("acct_shop", listOf(AccountProvider(openFsp, CredentialOrigin.PLATFORM, null))).data.single()
        assertEquals("platform", resource.source)
        assertNull(resource.notificationUrl)
        assertNull(resource.returnUrl)
    }
}
