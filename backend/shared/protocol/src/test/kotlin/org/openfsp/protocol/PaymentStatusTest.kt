package org.openfsp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentStatusTest {
    @Test
    fun `pending is the only non terminal state`() {
        assertEquals(listOf(PaymentStatus.PENDING), PaymentStatus.entries.filterNot { it.isTerminal })
    }

    @Test
    fun `only transitions from pending to a terminal state are allowed`() {
        for (from in PaymentStatus.entries) {
            for (to in PaymentStatus.entries) {
                assertEquals(from == PaymentStatus.PENDING && to.isTerminal, from.canTransitionTo(to), "$from -> $to")
            }
        }
    }

    @Test
    fun `a terminal state never moves again`() {
        assertFalse(PaymentStatus.SUCCEEDED.canTransitionTo(PaymentStatus.FAILED))
    }

    @Test
    fun `an unknown status is not mapped to any state`() {
        assertNull(PaymentStatus.fromWire("processing"))
        assertTrue(PaymentStatus.fromWire("succeeded")!!.isTerminal)
    }

    @Test
    fun `an unknown failure reason reads as unspecified`() {
        assertEquals(FailureReason.UNSPECIFIED, FailureReason.fromWire("fraud"))
    }
}
