package org.openfsp.validator.engine

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeliveryNoncesTest {
    @Test
    fun `a nonce checked again for its own delivery passes, and the same nonce on another delivery of the run is a replay`() {
        val nonces = DeliveryNonces()
        val until = Instant.now().plusSeconds(300)

        assertTrue(nonces.forDelivery("first").reserve("n-1", until))
        assertTrue(nonces.forDelivery("first").reserve("n-1", until))
        assertFalse(nonces.forDelivery("second").reserve("n-1", until))
        assertTrue(nonces.forDelivery("second").reserve("n-2", until))
    }
}
