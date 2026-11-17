package org.openfsp.gateway.apikeys

import org.openfsp.gateway.audit.spi.stubs.InMemoryAuditTrail
import org.openfsp.gateway.auth.ApiKeyService
import org.openfsp.gateway.auth.GatewayEnvironment
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.auth.spi.stubs.InMemoryApiKeys
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.spi.stubs.ImmediateTransactions
import org.openfsp.gateway.shared.ratelimits.RateLimitProperties
import org.openfsp.gateway.shared.ratelimits.RateLimits
import org.openfsp.gateway.shared.web.ProblemResponses
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiKeyFilterTest {
    private val keys = ApiKeyService(InMemoryApiKeys(), InMemoryAuditTrail(), BusinessCalendar(Clock.systemUTC()), GatewayEnvironment("test"), ImmediateTransactions())
    private val limits = RateLimits(RateLimitProperties(clientPerSecond = 0.001, clientBurst = 1.0), Clock.systemUTC())
    private val filter = ApiKeyFilter(keys, limits, ProblemResponses(JsonMapper.builder().build()))

    @Test
    fun `a rate limited request keeps its authenticated principal for the audit`() {
        val issued = keys.issue("shop", setOf(Scope.PAYMENTS_READ))
        val requests =
            (1..2).map {
                MockHttpServletRequest("GET", "/v1/payments/pay_1").apply { addHeader("Authorization", "Bearer ${issued.secret}") }
            }
        val statuses = requests.map { request -> MockHttpServletResponse().also { filter.doFilter(request, it, MockFilterChain()) }.status }

        assertEquals(listOf(200, 429), statuses)
        assertEquals(keys.authenticate(issued.secret)!!.principalId, ApiKeyFilter.of(requests[1])?.principalId)
    }
}
