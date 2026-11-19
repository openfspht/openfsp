package org.openfsp.gateway.shared.web

import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FailureBarrierTest {
    private val json = JsonMapper.builder().build()
    private val barrier = FailureBarrier(ProblemResponses(json, RequestPaths()), RequestPaths())
    private val failing = FilterChain { _, _ -> throw IllegalStateException("audit store down") }

    @Test
    fun `a failure in a later filter is answered as an internal error problem`() {
        val response = MockHttpServletResponse()
        barrier.doFilter(MockHttpServletRequest("GET", "/v1/payments/pay_1"), response, failing)

        assertEquals(500, response.status)
        assertEquals("application/problem+json", response.contentType)
        val problem = json.readTree(response.contentAsByteArray)
        assertEquals("https://openfsp.org/problems/internal-error", problem.get("type").asString())
        assertTrue(problem.get("retryable").asBoolean())
        assertEquals("unknown", problem.get("effect").asString())
        assertTrue(problem.get("request_id").asString().startsWith("req_"))
        assertTrue("audit store down" !in response.contentAsString)
    }

    @Test
    fun `a failure after the response is committed is not rewritten`() {
        val response = MockHttpServletResponse()
        val committing =
            FilterChain { _, served ->
                served.flushBuffer()
                throw IllegalStateException("late")
            }
        assertFailsWith<IllegalStateException> { barrier.doFilter(MockHttpServletRequest("GET", "/v1/payments"), response, committing) }
    }
}
