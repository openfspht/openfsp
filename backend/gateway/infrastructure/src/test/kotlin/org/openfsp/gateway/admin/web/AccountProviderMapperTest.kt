package org.openfsp.gateway.admin.web

import org.openfsp.gateway.provider.AccountProvider
import org.openfsp.gateway.provider.CredentialOrigin
import org.openfsp.gateway.provider.CredentialRevision
import org.openfsp.gateway.provider.CredentialStatus
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
        val retiredAt = at.plusSeconds(60)
        val revisions =
            listOf(
                CredentialRevision("cred_new", "acct_shop", "natcash", listOf("username"), CredentialStatus.ACTIVE, retiredAt),
                CredentialRevision("cred_old", "acct_shop", "natcash", listOf("username"), CredentialStatus.RETIRED, at, retiredAt = retiredAt),
            )
        val resource = mapper.toResource("acct_shop", AccountProvider(natCash, CredentialOrigin.ACCOUNT, revisions))
        assertEquals(
            AccountProviderResource(
                "natcash",
                "NatCash",
                "account",
                true,
                listOf("username"),
                retiredAt,
                URI("https://gateway.example/callbacks/natcash/accounts/acct_shop"),
                URI("https://gateway.example/return/natcash/accounts/acct_shop"),
                listOf(
                    CredentialRevisionResource("cred_new", "active", true, retiredAt, null, null),
                    CredentialRevisionResource("cred_old", "retired", true, at, retiredAt, null),
                ),
            ),
            resource,
        )
    }

    @Test
    fun `a provider without account credentials has no address of its own`() {
        val openFsp = CredentialedProvider("openfsp", "OpenFSP", emptyList(), hasPlatformCredentials = true, lookupNotifications = true)
        val resource = mapper.toCollection("acct_shop", listOf(AccountProvider(openFsp, CredentialOrigin.PLATFORM))).data.single()
        assertEquals("platform", resource.source)
        assertNull(resource.notificationUrl)
        assertNull(resource.returnUrl)
    }
}
