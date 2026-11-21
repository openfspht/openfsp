package org.openfsp.validator.engine

import org.openfsp.server.ExchangeRedaction
import org.openfsp.validator.suite.TargetKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigurationTest {
    private val configuration =
        Configuration(
            target = TargetKind.SERVER,
            baseUrl = "https://server.example",
            apiKey = "ofsp_test_key",
            inputs = mapOf("bounded-provider" to "mock_moncash", "expired-key" to "ofsp_test_expired"),
        )

    @Test
    fun `the published configuration masks secret inputs and keeps public ones`() {
        assertEquals(
            mapOf("bounded-provider" to "mock_moncash", "expired-key" to ExchangeRedaction.MASK),
            configuration.redacted("0.1.0")["inputs"],
        )
    }

    @Test
    fun `the secrets of a run are the api key and the secret inputs`() {
        assertEquals(listOf("ofsp_test_key", "ofsp_test_expired"), configuration.secrets)
    }
}
