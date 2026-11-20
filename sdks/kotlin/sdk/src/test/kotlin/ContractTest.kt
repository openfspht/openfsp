package org.openfsp.sdk

import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import org.openfsp.protocol.Locale
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.api.CapabilitiesResource
import org.openfsp.protocol.api.Fee
import org.openfsp.protocol.api.LimitsResource
import org.openfsp.protocol.api.PayerResource
import org.openfsp.protocol.api.PaymentEvent
import org.openfsp.protocol.api.PaymentRequest as WirePaymentRequest
import org.openfsp.protocol.api.PaymentResource
import org.openfsp.protocol.api.PaymentsCapabilityResource
import org.openfsp.protocol.api.ProviderCapabilitiesResource
import org.openfsp.protocol.api.StatementCollection
import org.openfsp.protocol.api.StatementEntryResource
import org.openfsp.sdk.model.Money
import org.openfsp.sdk.model.PaymentRequest
import org.openfsp.sdk.protocol.Json
import org.openfsp.sdk.protocol.Mapping
import org.openfsp.sdk.testing.FakeTransport
import org.openfsp.sdk.testing.Responses
import org.openfsp.sdk.testing.Wire
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import tools.jackson.databind.BeanProperty
import tools.jackson.databind.JavaType
import tools.jackson.databind.JsonNode
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper
import tools.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ContractTest {
    private val documents: Map<String, JsonNode> =
        File(SCHEMAS).listFiles { file -> file.extension == "json" }!!.associate { it.name to JsonMapper.builder().build().readTree(it) }
    private val registry =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12) { builder ->
            builder.schemas(documents.values.associate { it.get("\$id").stringValue() to it.toString() })
        }

    private fun problems(
        schema: String,
        instance: JsonNode,
    ): List<String> =
        registry
            .getSchema(SchemaLocation.of(documents.getValue(schema).get("\$id").stringValue()))
            .validate(instance)
            .map { "${it.instanceLocation}: ${it.message}" }

    private fun properties(
        schema: String,
        vararg path: String,
    ): Set<String> = path.fold(documents.getValue(schema)) { node, name -> node.get(name) }.get("properties").propertyNames().toSet()

    @Test
    fun `a request the SDK sends follows payment-request json`() {
        val request =
            PaymentRequest(
                reference = "INV-184",
                amount = Money.htg(1250),
                provider = "moncash",
                payerPhoneNumber = "+50934567890",
                description = "Order 184",
                returnUrl = "https://merchant.example/return",
                callbackUrl = "https://merchant.example/openfsp/webhooks",
                expiresAt = Instant.parse("2026-11-15T12:15:00.123456789Z"),
                locale = Locale.FR,
                metadata = mapOf("cart" to "42"),
            )
        val transport = FakeTransport(Responses.json(201, Wire.payment()))
        OpenFsp(OpenFspConfig("https://server.example/openfsp", "ofsp_test_key"), transport).payments.create(request, "key-1")

        val sent = Json.mapper.readTree(transport.requests.single().body!!)
        assertEquals(emptyList(), problems("payment-request.json", sent))
        assertEquals("2026-11-15T12:15:00.123Z", sent.get("expires_at").stringValue())
    }

    @TestFactory
    fun `the wire fixtures follow the schemas`(): List<DynamicTest> =
        listOf(
            "pending payment" to ("payment.json" to Wire.payment()),
            "succeeded payment" to ("payment.json" to Wire.payment(status = "succeeded")),
            "event" to ("event.json" to Wire.event()),
            "statement" to ("statement.json" to Wire.statement()),
            "problem" to ("problem.json" to Json.mapper.readTree(Responses.problem(422, "invalid-field").body)),
        ).map { (name, case) ->
            DynamicTest.dynamicTest(name) { assertEquals(emptyList(), problems(case.first, Json.mapper.valueToTree(case.second))) }
        }

    @TestFactory
    fun `the wire models carry every schema property`(): List<DynamicTest> =
        listOf(
            "payment-request.json" to (WirePaymentRequest::class.java to properties("payment-request.json")),
            "payment.json" to (PaymentResource::class.java to properties("payment.json")),
            "event.json" to (PaymentEvent::class.java to properties("event.json")),
            "capabilities.json" to (CapabilitiesResource::class.java to properties("capabilities.json")),
            "capabilities.json providers" to (ProviderCapabilitiesResource::class.java to properties("capabilities.json", "properties", "providers", "items")),
            "capabilities.json payments" to
                (PaymentsCapabilityResource::class.java to properties("capabilities.json", "properties", "providers", "items", "properties", "payments")),
            "statement.json" to (StatementCollection::class.java to properties("statement.json")),
            "discovery.json" to (org.openfsp.sdk.protocol.WireConfiguration::class.java to properties("discovery.json")),
            "money" to (org.openfsp.protocol.Money::class.java to properties("common.json", "\$defs", "money")),
            "fee" to (Fee::class.java to properties("common.json", "\$defs", "fee")),
            "payer" to (PayerResource::class.java to properties("common.json", "\$defs", "payer")),
        ).map { (name, case) ->
            DynamicTest.dynamicTest(name) { assertEquals(case.second, propertiesOf(case.first)) }
        }

    @Test
    fun `statement entries and limits carry every schema property`() {
        assertEquals(properties("statement.json", "\$defs", "entry"), propertiesOf(StatementEntryResource::class.java))
        assertEquals(
            documents.getValue("capabilities.json").at("/properties/providers/items/properties/payments/properties/limits/anyOf/0/properties").propertyNames().toSet(),
            propertiesOf(LimitsResource::class.java),
        )
    }

    private fun propertiesOf(type: Class<*>): Set<String> {
        val names = mutableSetOf<String>()
        Json.mapper.acceptJsonFormatVisitor(
            type,
            object : JsonFormatVisitorWrapper.Base() {
                override fun expectObjectFormat(type: JavaType): JsonObjectFormatVisitor =
                    object : JsonObjectFormatVisitor.Base(context) {
                        override fun property(writer: BeanProperty) {
                            names += writer.name
                        }

                        override fun optionalProperty(writer: BeanProperty) {
                            names += writer.name
                        }
                    }
            },
        )
        return names
    }

    private companion object {
        const val SCHEMAS = "../../../conformance/schemas"
    }
}
