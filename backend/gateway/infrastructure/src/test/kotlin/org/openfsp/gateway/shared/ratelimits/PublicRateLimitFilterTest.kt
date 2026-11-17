package org.openfsp.gateway.shared.ratelimits

import org.openfsp.gateway.shared.web.ProblemResponses
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals

class PublicRateLimitFilterTest {
    private val limits = RateLimits(RateLimitProperties(publicPerSecond = 0.001, publicBurst = 1.0), Clock.systemUTC())
    private val filter = PublicRateLimitFilter(limits, ProblemResponses(JsonMapper.builder().build()))

    private fun status(path: String): Int {
        val response = MockHttpServletResponse()
        filter.doFilter(MockHttpServletRequest("POST", path).apply { remoteAddr = "203.0.113.9" }, response, MockFilterChain())
        return response.status
    }

    @Test
    fun `provider callbacks share the public limit of the anonymous endpoints`() {
        assertEquals(200, status("/callbacks/mock_alpha"))
        assertEquals(429, status("/callbacks/mock_alpha/token"))
        assertEquals(200, status("/v1/payments"))
    }
}
