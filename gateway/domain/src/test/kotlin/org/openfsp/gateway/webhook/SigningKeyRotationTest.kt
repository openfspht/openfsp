package org.openfsp.gateway.webhook

import org.openfsp.gateway.TestClock
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.spi.stubs.ImmediateTransactions
import org.openfsp.gateway.common.spi.stubs.RecordingOperatorSignals
import org.openfsp.gateway.webhook.spi.stubs.InMemorySigningKeys
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SigningKeyRotationTest {
    private val clock = TestClock()
    private val store = InMemorySigningKeys()
    private val rotation = SigningKeyRotation(store, ImmediateTransactions(), RecordingOperatorSignals(), BusinessCalendar(clock))

    @Test
    fun `a new key is published a day before it signs, then the old one is retired and kept a day`() {
        val first = rotation.current()
        val next = rotation.rotate(immediate = false)
        assertEquals(first, rotation.current())
        assertEquals(listOf(next to KeyStatus.ACTIVE, first to KeyStatus.ACTIVE), rotation.published().map { it.kid to it.status })

        clock.advance(Duration.ofHours(24).plusSeconds(1))
        assertEquals(next, rotation.current())
        assertEquals(listOf(next to KeyStatus.ACTIVE, first to KeyStatus.RETIRED), rotation.published().map { it.kid to it.status })

        clock.advance(Duration.ofHours(24).plusSeconds(1))
        assertEquals(listOf(next), rotation.published().map { it.kid })
    }

    @Test
    fun `an immediate rotation signs at once and withdraws the compromised key`() {
        val compromised = rotation.current()
        val replacement = rotation.rotate(immediate = true)
        assertNotEquals(compromised, replacement)
        assertEquals(replacement, rotation.current())
        assertEquals(listOf(replacement), rotation.published().map { it.kid })
    }
}
