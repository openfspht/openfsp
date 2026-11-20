package org.openfsp.gateway.auditing

import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServlet
import org.openfsp.gateway.audit.AuditRecord
import org.openfsp.gateway.audit.spi.AuditTrail
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.shared.web.HttpExchanges
import org.openfsp.gateway.shared.web.RequestPaths
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals

class AuditFilterTest {
    @Test
    fun `a failing audit write never replaces the response already served`() {
        val failing =
            object : AuditTrail {
                override fun record(record: AuditRecord) = error("the database is down")
            }
        val filter = AuditFilter(failing, BusinessCalendar(Clock.systemUTC()), RequestPaths(), HttpExchanges())
        val response = MockHttpServletResponse()
        val served =
            MockFilterChain(
                object : HttpServlet() {
                    override fun service(
                        request: ServletRequest,
                        response: ServletResponse,
                    ) {
                        response.writer.write("""{"id":"pay_1"}""")
                    }
                },
            )

        filter.doFilter(MockHttpServletRequest("GET", "/v1/payments/pay_1"), response, served)

        assertEquals("""{"id":"pay_1"}""", response.contentAsString)
    }
}
