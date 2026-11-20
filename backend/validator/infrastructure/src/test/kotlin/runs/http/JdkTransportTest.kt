package org.openfsp.validator.runs.http

import org.openfsp.validator.engine.OutgoingRequest
import org.openfsp.validator.engine.TransportException
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JdkTransportTest {
    @Test
    fun `an interrupted request names its URL without its secrets`() {
        val request = OutgoingRequest("GET", "http://127.0.0.1:9/v1/things?api_key=ofsp_test_secret", emptyMap(), null)
        Thread.currentThread().interrupt()
        val failure =
            try {
                assertFailsWith<TransportException> { JdkTransport(Duration.ofSeconds(1)).send(request) }
            } finally {
                Thread.interrupted()
            }
        assertTrue(failure.message.orEmpty().contains("/v1/things"), failure.message)
        assertFalse(failure.message.orEmpty().contains("ofsp_test_secret"), failure.message)
    }
}
