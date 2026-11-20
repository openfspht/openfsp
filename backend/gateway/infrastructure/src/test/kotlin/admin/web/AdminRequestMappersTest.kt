package org.openfsp.gateway.admin.web

import org.openfsp.gateway.account.MerchantAccount
import org.openfsp.gateway.auth.ApiKeyState
import org.openfsp.gateway.auth.IssuedKey
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.payment.Attestation
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.PaymentStatus
import org.openfsp.server.FieldErrorCode
import java.net.URI
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdminRequestMappersTest {
    private val at = Instant.parse("2026-11-17T10:15:30Z")

    @Test
    fun `an account is opened under its trimmed name and listed with its dates`() {
        val mapper = AccountResourceMapper()
        assertEquals("shop", mapper.toName(AccountRequest(" shop ")))
        assertEquals("/name" to FieldErrorCode.REQUIRED, assertFailsWith<OpenFspException> { mapper.toName(AccountRequest("  ")) }.errors.single().let { it.field to it.code })
        val account = MerchantAccount("acct_1", "shop", at, at.plusSeconds(60))
        assertEquals(AccountCollection(listOf(AccountResource("acct_1", "shop", at, at.plusSeconds(60)))), mapper.toCollection(listOf(account)))
    }

    @Test
    fun `a key request is trimmed, its scopes read from the wire and its status filter parsed`() {
        val mapper = AdminKeyMapper()
        assertEquals(KeyIssue("acct_1", "shop", setOf(Scope.PAYMENTS_READ), at), mapper.toIssue(KeyRequest(" acct_1 ", " shop ", listOf("payments:read"), at)))
        assertEquals("/scopes", assertFailsWith<OpenFspException> { mapper.toIssue(KeyRequest("acct_1", "shop", listOf("payments:delete"))) }.errors.single().field)
        assertEquals("/principal", assertFailsWith<OpenFspException> { mapper.toIssue(KeyRequest("acct_1", " ", emptyList())) }.errors.single().field)
        assertEquals(ApiKeyState.REVOKED, mapper.toState("Revoked"))
        assertFailsWith<OpenFspException> { mapper.toState("lost") }
        assertEquals(IssuedKeyResource("key_1", "secret"), mapper.toResource(IssuedKey("key_1", "secret", "prn_1", "acct_1")))
    }

    @Test
    fun `an attestation names a terminal state and only a failure carries a reason`() {
        val mapper = AttestationRequestMapper()
        assertEquals(
            Attestation("Ops", PaymentStatus.FAILED, "ticket 12", FailureReason.DECLINED),
            mapper.toAttestation(AttestationRequest("Ops", "failed", "ticket 12", "declined")),
        )
        assertEquals("/status", assertFailsWith<OpenFspException> { mapper.toAttestation(AttestationRequest("Ops", "pending", "ticket 12")) }.errors.single().field)
        assertEquals("/attestation", assertFailsWith<OpenFspException> { mapper.toAttestation(AttestationRequest("Ops", "succeeded", "ticket 12", "declined")) }.errors.single().field)
    }

    @Test
    fun `an endpoint address must parse as a URL`() {
        val mapper = EndpointRequestMapper()
        assertEquals(URI("https://shop.example/hooks"), mapper.toUrl(EndpointRequest("acct_1", "shop", " https://shop.example/hooks ", emptySet())))
        assertEquals("/url", assertFailsWith<OpenFspException> { mapper.toUrl(EndpointRequest("acct_1", "shop", "https://exa mple", emptySet())) }.errors.single().field)
        assertEquals(RotatedKeyResource("kid_2"), mapper.toResource("kid_2"))
    }
}
