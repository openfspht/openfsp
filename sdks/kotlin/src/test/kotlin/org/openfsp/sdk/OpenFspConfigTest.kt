package org.openfsp.sdk

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class OpenFspConfigTest {
    @Test
    fun `normalizes the base url under its path prefix`() {
        assertEquals("https://server.example/openfsp/", OpenFspConfig("https://server.example/openfsp", "k").baseUrl.toString())
    }

    @Test
    fun `refuses plain http outside a loopback host unless allowed`() {
        assertFailsWith<ConfigurationException> { OpenFspConfig("http://server.example", "k") }
        assertEquals("http", OpenFspConfig("http://127.0.0.1:8080", "k").baseUrl.scheme)
        assertEquals("server", OpenFspConfig("http://server:8080", "k", allowPlainHttp = true).baseUrl.host)
    }

    @Test
    fun `rejects unusable settings`() {
        assertFailsWith<ConfigurationException> { OpenFspConfig("server.example", "k") }
        assertFailsWith<ConfigurationException> { OpenFspConfig("https://user:secret@server.example", "k") }
        assertFailsWith<ConfigurationException> { OpenFspConfig("ftp://server.example", "k") }
        assertFailsWith<ConfigurationException> { OpenFspConfig("https://server.example", "a key") }
        assertFailsWith<ConfigurationException> { OpenFspConfig("https://server.example", "k", timeout = Duration.ZERO) }
    }

    @Test
    fun `reads the environment`() {
        val config =
            OpenFspConfig.fromEnv(
                mapOf(
                    "OPENFSP_BASE_URL" to "http://server:8080",
                    "OPENFSP_API_KEY" to "ofsp_test_key",
                    "OPENFSP_ALLOW_PLAIN_HTTP" to "true",
                    "OPENFSP_TIMEOUT_MS" to "5000",
                    "OPENFSP_WEBHOOK_URL" to "https://shop.example/webhooks",
                ),
            )
        assertEquals(Duration.ofSeconds(5), config.timeout)
        assertEquals("https://shop.example/webhooks", config.webhookUrl)
        assertEquals("OPENFSP_BASE_URL is required", assertFailsWith<ConfigurationException> { OpenFspConfig.fromEnv(emptyMap()) }.message)
    }

    @Test
    fun `never shows the api key`() {
        assertFalse("secret" in OpenFspConfig("https://server.example", "ofsp_live_secret").toString())
    }
}
