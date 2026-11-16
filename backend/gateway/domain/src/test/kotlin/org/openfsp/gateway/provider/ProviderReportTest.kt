package org.openfsp.gateway.provider

import org.openfsp.protocol.PaymentStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProviderReportTest {
    private fun report(reference: String) = ProviderReport("order1", reference, PaymentStatus.PENDING, null)

    @Test
    fun `a provider reference of 255 code points is accepted, even outside the basic plane`() {
        val astral = "💳".repeat(255)
        assertEquals(astral, report(astral).providerReference)
    }

    @Test
    fun `a provider reference longer than 255 code points is a provider error`() {
        assertFailsWith<ProviderFailure.Rejected> { report("x".repeat(256)) }
    }
}
