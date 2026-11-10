package org.openfsp.gateway.providers

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.providers.simulated.SimulatedCallback
import org.openfsp.gateway.providers.simulated.SimulatedMapper
import org.openfsp.protocol.FailureReason
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals

class SimulatedMapperTest {
    private val mapper = SimulatedMapper(DecimalAmounts(), BusinessCalendar(Clock.systemUTC()))

    private fun failed(
        reason: String?,
        message: String? = null,
    ) = mapper.toReport(SimulatedCallback("MA-1", "order1", "FAILED", "1250.00", reason, message))

    @Test
    fun `a payer cancellation is reported as payer_canceled`() {
        assertEquals(FailureReason.PAYER_CANCELED, failed("PAYER_CANCELED").failureReason)
    }

    @Test
    fun `the failure detail relays the operator code and message`() {
        assertEquals(mapOf("code" to "DECLINED", "message" to "Card refused by issuer"), failed("DECLINED", "Card refused by issuer").failureDetail)
    }

    @Test
    fun `the failure detail leaves out what the operator did not send`() {
        assertEquals(mapOf("code" to "DECLINED"), failed("DECLINED").failureDetail)
        assertEquals(null, failed(null).failureDetail)
    }
}
