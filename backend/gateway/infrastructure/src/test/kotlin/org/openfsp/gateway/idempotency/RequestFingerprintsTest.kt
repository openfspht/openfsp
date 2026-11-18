package org.openfsp.gateway.idempotency

import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RequestFingerprintsTest {
    private val fingerprints = RequestFingerprints(JsonMapper.builder().build())

    private fun of(body: String) = fingerprints.of(body.toByteArray()).value

    private fun sha256(text: String) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray()))

    @Test
    fun `the fingerprint is the SHA-256 of the JCS form of the body`() {
        assertEquals(sha256("""{"a":"x\n\u001f","b":[1,true,null],"c":{"d":2}}"""), of("""{ "c": {"d": 2}, "b": [1, true, null], "a": "x\n\u001F" }"""))
        assertEquals(sha256("{}"), fingerprints.of(ByteArray(0)).value)
    }

    @Test
    fun `null members are removed at every level`() {
        assertEquals(of("""{"a":1,"b":{"c":2}}"""), of("""{"a":1,"x":null,"b":{"c":2,"y":null}}"""))
    }

    @Test
    fun `strings are compared in NFC except metadata values`() {
        assertEquals(of("""{"description":"café"}"""), of("""{"description":"café"}"""))
        assertNotEquals(of("""{"metadata":{"k":"café"}}"""), of("""{"metadata":{"k":"café"}}"""))
    }

    @Test
    fun `the legacy fingerprint of records written before JCS is still computed`() {
        val legacy = JsonMapper.builder().build().let { json -> sha256(json.writeValueAsString(json.readTree("""{"a":1,"b":null}"""))) }
        assertEquals(legacy, fingerprints.of("""{"b":null,"a":1}""".toByteArray()).legacy)
    }
}
