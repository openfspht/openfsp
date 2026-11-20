package org.openfsp.server.idempotency

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import tools.jackson.databind.json.JsonMapper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RequestFingerprintsTest {
    private val json = JsonMapper.builder().build()

    @TestFactory
    fun `shared fingerprint vectors`(): List<DynamicTest> =
        json.readTree(File("../../../conformance/vectors/fingerprints.json")).get("cases").values().map { vector ->
            DynamicTest.dynamicTest(vector.get("name").stringValue()) {
                val body = vector.get("body").stringValue().takeIf { it.isNotEmpty() }?.let { json.readTree(it) }
                assertEquals(vector.get("canonical").stringValue(), RequestFingerprints.canonical(body))
                assertEquals(vector.get("fingerprint").stringValue(), RequestFingerprints.of(body))
            }
        }

    @Test
    fun `integers too long for a plain form keep every digit`() {
        val long = json.readTree("1234567890123456789012345678900")

        assertEquals("1.2345678901234567890123456789E+30", RequestFingerprints.canonical(long))
        assertNotEquals(RequestFingerprints.of(long), RequestFingerprints.of(json.readTree("1234567890123456789012345678901")))
    }
}
