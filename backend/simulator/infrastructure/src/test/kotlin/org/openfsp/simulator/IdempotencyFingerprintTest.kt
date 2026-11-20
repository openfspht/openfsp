package org.openfsp.simulator

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.openfsp.simulator.dialects.openfsp.OpenFspFingerprints
import tools.jackson.databind.json.JsonMapper
import java.io.File
import kotlin.test.assertEquals

class IdempotencyFingerprintTest {
    private val json = JsonMapper.builder().build()
    private val fingerprints = OpenFspFingerprints()

    @TestFactory
    fun `shared fingerprint vectors`(): List<DynamicTest> =
        json.readTree(File("../../../conformance/vectors/fingerprints.json")).get("cases").values().map { vector ->
            DynamicTest.dynamicTest(vector.get("name").stringValue()) {
                val body = vector.get("body").stringValue().takeIf { it.isNotEmpty() }?.let(json::readTree)
                assertEquals(vector.get("fingerprint").stringValue(), fingerprints.of(body))
            }
        }
}
