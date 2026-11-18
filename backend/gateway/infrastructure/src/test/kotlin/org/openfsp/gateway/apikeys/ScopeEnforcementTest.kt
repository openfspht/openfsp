package org.openfsp.gateway.apikeys

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.gateway.account.MerchantAccount
import org.openfsp.gateway.account.spi.stubs.InMemoryMerchantAccounts
import org.openfsp.gateway.audit.spi.stubs.InMemoryAuditTrail
import org.openfsp.gateway.auth.ApiKeyAuthority
import org.openfsp.gateway.auth.GatewayEnvironment
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.auth.spi.stubs.InMemoryApiKeys
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.spi.stubs.ImmediateTransactions
import org.openfsp.gateway.shared.ratelimits.RateLimitProperties
import org.openfsp.gateway.shared.ratelimits.RateLimits
import org.openfsp.gateway.shared.web.BearerTokens
import org.openfsp.gateway.shared.web.ProblemResponses
import org.openfsp.protocol.ProblemType
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.method.HandlerMethod
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals

class ScopeEnforcementTest {
    private val keys = ApiKeyAuthority(InMemoryApiKeys(), InMemoryMerchantAccounts(), InMemoryAuditTrail(), BusinessCalendar(Clock.systemUTC()), GatewayEnvironment("test"), ImmediateTransactions())
    private val filter = ApiKeyFilter(keys, RateLimits(RateLimitProperties(), Clock.systemUTC()), ProblemResponses(JsonMapper.builder().build()), BearerTokens())
    private val enforcement = ScopeEnforcement()

    @Suppress("unused")
    class Handlers {
        @RequiresScope(Scope.PAYMENTS_READ)
        fun guarded() = Unit

        fun unguarded() = Unit
    }

    private fun outcome(method: String): ProblemType? {
        val secret = keys.issue(MerchantAccount.DEFAULT, "shop", Scope.entries.toSet()).secret
        var problem: ProblemType? = null
        val servlet =
            object : HttpServlet() {
                override fun service(
                    request: HttpServletRequest,
                    response: HttpServletResponse,
                ) {
                    problem = runCatching { enforcement.preHandle(request, response, HandlerMethod(Handlers(), method)) }.exceptionOrNull()?.let { (it as OpenFspException).type }
                }
            }
        val request = MockHttpServletRequest("GET", "/v1/payments").apply { addHeader("Authorization", "Bearer $secret") }
        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain(servlet))
        return problem
    }

    @Test
    fun `an API handler without a declared scope is refused even to a key holding every scope`() {
        assertEquals(null, outcome("guarded"))
        assertEquals(ProblemType.FORBIDDEN, outcome("unguarded"))
    }
}
