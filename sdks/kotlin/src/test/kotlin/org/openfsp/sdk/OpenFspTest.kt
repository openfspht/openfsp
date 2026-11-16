package org.openfsp.sdk

import org.openfsp.sdk.testing.FakeTransport
import org.openfsp.sdk.testing.Responses
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class OpenFspTest {
    private val provider =
        mapOf(
            "id" to "moncash",
            "display_name" to "MonCash",
            "capabilities" to listOf("payments.redirect"),
            "payments" to
                mapOf(
                    "next_actions" to listOf("redirect"),
                    "payer_required" to false,
                    "return_url_required" to true,
                    "expiry_guaranteed" to true,
                    "locales" to listOf("fr"),
                ),
        )

    @Test
    fun `lists providers`() {
        val capabilities = Responses.json(200, mapOf("protocol_version" to "0.1", "providers" to listOf(provider)))
        val openfsp = OpenFsp(OpenFspConfig("https://gateway.example", "k"), FakeTransport(capabilities, capabilities))
        assertEquals(listOf("MonCash"), openfsp.providers().map { it.displayName })
        assertNull(openfsp.provider("natcash"))
    }

    @Test
    fun `reads the configuration document without credentials`() {
        val transport =
            FakeTransport(
                Responses.json(
                    200,
                    mapOf(
                        "issuer" to "https://gateway.example",
                        "protocol_version" to "0.1",
                        "api_base" to "https://gateway.example/v1",
                        "payments_endpoint" to "https://gateway.example/v1/payments",
                        "capabilities_endpoint" to "https://gateway.example/v1/capabilities",
                        "webhook_keys" to emptyList<Any>(),
                    ),
                ),
            )
        OpenFsp(OpenFspConfig("https://gateway.example", "k"), transport).configuration()
        assertEquals("/.well-known/openfsp-configuration", transport.requests.single().uri.path)
        assertNull(transport.requests.single().header("authorization"))
    }

    @Test
    fun `is built from the environment and never shows its key`() {
        val openfsp = OpenFsp.fromEnv(mapOf("OPENFSP_BASE_URL" to "https://gateway.example", "OPENFSP_API_KEY" to "ofsp_live_secret"))
        assertEquals("OpenFsp(https://gateway.example/)", openfsp.toString())
        assertFalse("secret" in openfsp.config.toString())
    }
}
