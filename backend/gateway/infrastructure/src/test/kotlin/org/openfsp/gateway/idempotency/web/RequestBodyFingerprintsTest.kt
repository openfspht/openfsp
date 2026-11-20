package org.openfsp.gateway.idempotency.web

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.openfsp.server.Digests
import org.openfsp.server.idempotency.RequestFingerprints
import tools.jackson.databind.json.JsonMapper
import java.io.File
import kotlin.test.assertEquals

class RequestBodyFingerprintsTest {
    private val json = JsonMapper.builder().build()
    private val fingerprints = RequestBodyFingerprints(json, RequestFingerprints(Digests()))

    @TestFactory
    fun `shared fingerprint vectors`(): List<DynamicTest> =
        json.readTree(File("../../../conformance/vectors/fingerprints.json")).get("cases").values().map { vector ->
            DynamicTest.dynamicTest(vector.get("name").stringValue()) {
                assertEquals(vector.get("fingerprint").stringValue(), fingerprints.of(vector.get("body").stringValue().toByteArray()).value)
            }
        }
}
