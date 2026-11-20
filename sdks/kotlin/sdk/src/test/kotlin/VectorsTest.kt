package org.openfsp.sdk

import org.openfsp.protocol.webhook.KeyStatus
import org.openfsp.sdk.model.WebhookKey
import org.openfsp.sdk.testing.FakeClock
import org.openfsp.sdk.webhooks.WebhookDelivery
import org.openfsp.sdk.webhooks.Webhooks
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

class VectorsTest {
    private val vectors: JsonNode = JsonMapper.builder().build().readTree(File("../../../conformance/vectors/webhooks.json"))

    @TestFactory
    fun `shared webhook vectors`(): List<DynamicTest> {
        val keys = vectors.get("keys").values().map(::webhookKey)
        return vectors.get("cases").values().map { vector ->
            DynamicTest.dynamicTest(vector.text("name")) {
                val clock = FakeClock(Instant.EPOCH)
                val webhooks = Webhooks.withKeys(keys, clock = clock)
                vector.get("steps").values().forEach { step ->
                    clock.advance(Duration.between(clock.now(), Instant.ofEpochSecond(step.get("now").longValue())))
                    assertEquals(step.get("valid").booleanValue(), runCatching { webhooks.verify(delivery(step.get("delivery"))) }.isSuccess)
                }
            }
        }
    }

    private fun webhookKey(key: JsonNode) =
        WebhookKey(key.text("kid"), key.text("kty"), key.textOrNull("crv"), key.textOrNull("alg"), key.textOrNull("use"), key.textOrNull("x"), KeyStatus.fromWire(key.textOrNull("openfsp_status")), key.textOrNull("y"))

    private fun delivery(delivery: JsonNode): WebhookDelivery {
        val headers = delivery.get("headers").properties().associate { it.key to it.value.stringValue() }
        return WebhookDelivery(delivery.text("method"), delivery.text("url"), headers, delivery.text("body").toByteArray())
    }

    private fun JsonNode.text(name: String): String = get(name).stringValue()

    private fun JsonNode.textOrNull(name: String): String? = get(name)?.stringValue()
}
