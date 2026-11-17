package org.openfsp.gateway.webhook

import org.openfsp.gateway.TestClock
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.spi.stubs.ImmediateTransactions
import org.openfsp.gateway.common.spi.stubs.RecordingOperatorSignals
import org.openfsp.gateway.webhook.spi.stubs.InMemorySigningKeys
import org.openfsp.protocol.webhook.KeyStatus
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SigningKeyRotationTest {
    private val clock = TestClock()
    private val store = InMemorySigningKeys()
    private val rotation = SigningKeyRotation(store, ImmediateTransactions(), RecordingOperatorSignals(), BusinessCalendar(clock), KeyRotationPolicy(Duration.ofDays(30)))

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
    fun `the first key is created under the key lock, once`() {
        val kid = rotation.current()
        assertEquals(kid, rotation.current())
        assertEquals(1, store.keys.size)
        assertEquals(1, store.locks)
    }

    @Test
    fun `publishing the keys writes nothing and reads a superseded key as retired`() {
        val first = rotation.current()
        val next = rotation.rotate(immediate = false)
        clock.advance(Duration.ofHours(24).plusSeconds(1))
        val before = store.keys.toList()

        assertEquals(listOf(next to KeyStatus.ACTIVE, first to KeyStatus.RETIRED), rotation.published().map { it.kid to it.status })
        assertEquals(before, store.keys)
        assertEquals(emptyList(), SigningKeyRotation(InMemorySigningKeys(), ImmediateTransactions(), RecordingOperatorSignals(), BusinessCalendar(clock), KeyRotationPolicy(null)).published())
    }

    @Test
    fun `an immediate rotation signs at once and withdraws the compromised key`() {
        val compromised = rotation.current()
        val replacement = rotation.rotate(immediate = true)
        assertNotEquals(compromised, replacement)
        assertEquals(replacement, rotation.current())
        assertEquals(listOf(replacement), rotation.published().map { it.kid })
    }

    @Test
    fun `a key is rotated automatically a day before its period ends, never twice`() {
        val first = rotation.current()
        assertEquals(null, rotation.rotateIfDue())
        clock.advance(Duration.ofDays(29).plusHours(1))
        val successor = rotation.rotateIfDue()
        assertNotEquals(first, successor)
        assertEquals(null, rotation.rotateIfDue())
        assertEquals(first, rotation.current())
        clock.advance(Duration.ofHours(24))
        assertEquals(successor, rotation.current())
    }
}
