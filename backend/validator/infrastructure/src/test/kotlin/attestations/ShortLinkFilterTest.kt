package org.openfsp.validator.attestations

import org.openfsp.validator.attestation.AttestationKey
import org.openfsp.validator.attestation.AttestationSettings
import org.openfsp.validator.attestation.IssuedAttestation
import org.openfsp.validator.attestation.StoredAttestation
import org.openfsp.validator.attestation.api.ReadAttestations
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ShortLinkFilterTest {
    private val id = UUID.fromString("d637d955-30c1-4ca0-a5a6-16d8b41214de")

    private val attestations =
        object : ReadAttestations {
            override val publicUrl = "https://validator.test"

            override fun find(id: UUID): IssuedAttestation = throw UnsupportedOperationException()

            override fun forRun(runId: UUID): IssuedAttestation = throw UnsupportedOperationException()

            override fun pageUrl(id: UUID) = "$publicUrl/attestations/$id"

            override fun shareUrl(stored: StoredAttestation) = "https://l.test/${stored.shortCode}"

            override fun byShortCode(code: String) = id.takeIf { code == "Xk3P9qAb" }

            override fun publishedKeys(): List<AttestationKey> = emptyList()
        }

    private val filter = ShortLinkFilter(AttestationSettings("https://validator.test", "https://l.test"), attestations)

    private fun get(
        host: String,
        path: String,
    ): Pair<MockHttpServletResponse, MockFilterChain> {
        val request = MockHttpServletRequest("GET", path).apply { serverName = host }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        filter.doFilter(request, response, chain)
        return response to chain
    }

    @Test
    fun `a known code on the short host redirects to the attestation page`() {
        val (response, chain) = get("l.test", "/Xk3P9qAb")

        assertEquals(301, response.status)
        assertEquals("https://validator.test/attestations/$id", response.getHeader("Location"))
        assertNull(chain.request)
    }

    @Test
    fun `an unknown code is not found and the bare short host leads to the validator`() {
        assertEquals(404, get("l.test", "/nothing00").first.status)
        assertEquals("https://validator.test", get("l.test", "/").first.getHeader("Location"))
    }

    @Test
    fun `requests to the validator host pass through`() {
        val (response, chain) = get("validator.test", "/Xk3P9qAb")

        assertEquals(200, response.status)
        assertNotNull(chain.request)
    }
}
