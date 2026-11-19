package org.openfsp.protocol.idempotency

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.openfsp.protocol.Digests
import org.openfsp.protocol.json.JsonValue
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RequestFingerprintsTest {
    private val json = JsonMapper.builder().build()
    private val fingerprints = RequestFingerprints(Digests())

    @TestFactory
    fun `shared fingerprint vectors`(): List<DynamicTest> =
        json.readTree(File("../../conformance/vectors/fingerprints.json")).get("cases").values().map { vector ->
            DynamicTest.dynamicTest(vector.get("name").stringValue()) {
                val body = vector.get("body").stringValue().takeIf { it.isNotEmpty() }?.let { valueOf(json.readTree(it)) }
                assertEquals(vector.get("canonical").stringValue(), fingerprints.canonical(body))
                assertEquals(vector.get("fingerprint").stringValue(), fingerprints.of(body))
            }
        }

    @Test
    fun `integers too long for a plain form keep every digit`() {
        val long = JsonValue.JsonNumber(BigDecimal("1234567890123456789012345678900"))

        assertEquals("1.2345678901234567890123456789E+30", fingerprints.canonical(long))
        assertNotEquals(fingerprints.of(long), fingerprints.of(JsonValue.JsonNumber(BigDecimal("1234567890123456789012345678901"))))
    }

    private fun valueOf(node: JsonNode): JsonValue =
        when {
            node.isObject -> JsonValue.JsonObject(node.propertyNames().associateWith { valueOf(node.get(it)) })
            node.isArray -> JsonValue.JsonArray(node.values().map(::valueOf))
            node.isString -> JsonValue.JsonString(node.stringValue())
            node.isIntegralNumber -> JsonValue.JsonNumber(BigDecimal(node.bigIntegerValue()))
            node.isNumber -> JsonValue.JsonNumber(node.decimalValue())
            node.isBoolean -> JsonValue.JsonBoolean(node.booleanValue())
            else -> JsonValue.JsonNull
        }
}
