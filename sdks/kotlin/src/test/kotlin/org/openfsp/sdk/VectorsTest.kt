package org.openfsp.sdk

import org.openfsp.protocol.webhook.WebhookJwk
import org.openfsp.sdk.testing.FakeClock
import org.openfsp.sdk.webhooks.WebhookDelivery
import org.openfsp.sdk.webhooks.Webhooks
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals

class VectorsTest {
    private val vectors: JsonNode = JsonMapper.builder().build().readTree(File("../../conformance/vectors/webhooks.json"))

    @TestFactory
    fun `shared webhook vectors`(): List<DynamicTest> {
        val key = vectors.get("key")
        val jwk = WebhookJwk(kid = key.text("kid"), x = key.text("x"), openfspStatus = key.text("openfsp_status"))
        val cases = vectors.get("cases")
        return (0 until cases.size()).map { cases.get(it) }.map { vector ->
            DynamicTest.dynamicTest(vector.text("name")) {
                val delivery = vector.get("delivery")
                val headers = delivery.get("headers").properties().associate { it.key to it.value.stringValue() }
                val received = WebhookDelivery(delivery.text("method"), delivery.text("url"), headers, delivery.text("body").toByteArray())
                val webhooks = Webhooks.withKeys(listOf(jwk), clock = FakeClock(Instant.ofEpochSecond(vector.get("now").longValue())))
                assertEquals(vector.get("valid").booleanValue(), runCatching { webhooks.verify(received) }.isSuccess)
            }
        }
    }

    private fun JsonNode.text(name: String): String = get(name).stringValue()
}
