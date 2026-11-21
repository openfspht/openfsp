package org.openfsp.server

import kotlin.test.Test
import kotlin.test.assertEquals

class ExchangeRedactionTest {
    @Test
    fun `secret json fields are masked and the others kept`() {
        val body = """{"username":"merchant","password":"p@ss \"x\"","signature":"abc","amount":"10.00"}"""
        assertEquals("""{"username":"merchant","password":"[REDACTED]","signature":"[REDACTED]","amount":"10.00"}""", ExchangeRedaction.body(body))
    }

    @Test
    fun `secret form and query values are masked`() {
        assertEquals("grant_type=client_credentials&client_secret=[REDACTED]", ExchangeRedaction.body("grant_type=client_credentials&client_secret=s3cret"))
        assertEquals("token=[REDACTED]&orderId=o1", ExchangeRedaction.pairs("token=abc&orderId=o1"))
    }

    @Test
    fun `every field whose name holds key, secret or token is masked, dotted names included`() {
        val body = """{"restricted-key":"ofsp_test_a","statements_key":"ofsp_test_b","client.secret":"c","refresh_token":"d","reference":"o1"}"""
        assertEquals("""{"restricted-key":"[REDACTED]","statements_key":"[REDACTED]","client.secret":"[REDACTED]","refresh_token":"[REDACTED]","reference":"o1"}""", ExchangeRedaction.body(body))
        assertEquals("public_key=[REDACTED]&orderId=o1", ExchangeRedaction.pairs("public_key=abc&orderId=o1"))
    }

    @Test
    fun `secret headers are masked`() {
        assertEquals(mapOf("authorization" to "[REDACTED]", "content-type" to "application/json"), ExchangeRedaction.headers(mapOf("authorization" to "Bearer k", "content-type" to "application/json")))
    }

    @Test
    fun `a json body cut by the size limit is not recorded`() {
        assertEquals("[REDACTED]", ExchangeRedaction.body("""{"amount":"10.00","client_secret":"s3cr\"et-cut"""))
        assertEquals("[REDACTED]", ExchangeRedaction.body("""{"reference":"o1-cut"""))
    }

    @Test
    fun `a member name spelled with escapes is masked as the parser reads it`() {
        assertEquals("""{"client_secret":"[REDACTED]","nested":[{"api_key":"[REDACTED]","amount":12.50}]}""", ExchangeRedaction.body("""{"client\u005fsecret":"s","nested":[{"api_key":{"v":"k"},"amount":12.50}]}"""))
    }

    @Test
    fun `a body that is neither json nor a form is dropped when it names a secret`() {
        assertEquals("[REDACTED]", ExchangeRedaction.body("\uFEFF{\"token\":\"t\""))
        assertEquals("""{"token":"[REDACTED]"}""", ExchangeRedaction.body("\uFEFF{\"token\":\"t\"}"))
        assertEquals("[REDACTED]", ExchangeRedaction.body("""note {"client_secret":"s"}"""))
        assertEquals("Service unavailable", ExchangeRedaction.body("Service unavailable"))
    }
}
