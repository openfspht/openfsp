package org.openfsp.simulator

import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.dialects.openfsp.OpenFspIdempotency
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IdempotencyFingerprintTest {
    private val json = JsonMapper.builder().build()
    private val idempotency = OpenFspIdempotency(json, SimulationClock(Clock.systemUTC()))

    private fun of(body: String) = idempotency.fingerprint(json.readTree(body))

    @Test
    fun `members valued null, member order and the NFC form of strings do not change the fingerprint`() {
        val plain = of("""{"reference":"r-1","description":"café","amount":{"amount":100,"currency":"HTG"}}""")

        assertEquals(plain, of("""{"amount":{"currency":"HTG","amount":100},"description":"café","reference":"r-1","metadata":null}"""))
        assertEquals(of("{}"), idempotency.fingerprint(null))
        assertNotEquals(plain, of("""{"reference":"r-1","description":"cafe","amount":{"amount":100,"currency":"HTG"}}"""))
    }
}
