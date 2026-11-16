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
    fun `secret headers are masked`() {
        assertEquals(mapOf("authorization" to "[REDACTED]", "content-type" to "application/json"), ExchangeRedaction.headers(mapOf("authorization" to "Bearer k", "content-type" to "application/json")))
    }
}
