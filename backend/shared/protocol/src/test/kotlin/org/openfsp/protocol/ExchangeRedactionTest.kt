package org.openfsp.protocol

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
}
