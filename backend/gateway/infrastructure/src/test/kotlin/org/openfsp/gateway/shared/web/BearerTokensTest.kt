package org.openfsp.gateway.shared.web

import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BearerTokensTest {
    private val bearer = BearerTokens()

    private fun of(vararg authorization: String) = bearer.of(MockHttpServletRequest().apply { authorization.forEach { addHeader("Authorization", it) } })

    @Test
    fun `the bearer scheme is read in any case`() {
        assertEquals("secret", of("Bearer secret"))
        assertEquals("secret", of("bearer secret"))
        assertEquals("secret", of("BEARER secret"))
    }

    @Test
    fun `several Authorization headers, another scheme or an empty token give no token`() {
        assertNull(of("Bearer one", "Bearer two"))
        assertNull(of("Basic c2VjcmV0"))
        assertNull(of("Bearer "))
        assertNull(of())
    }
}
