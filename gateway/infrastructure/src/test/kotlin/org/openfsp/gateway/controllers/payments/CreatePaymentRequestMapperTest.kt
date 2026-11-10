package org.openfsp.gateway.controllers.payments

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.protocol.FieldError
import org.openfsp.protocol.FieldErrorCode
import org.openfsp.protocol.ProblemType
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CreatePaymentRequestMapperTest {
    private val mapper = CreatePaymentRequestMapper(GatewayProperties(GatewayProperties.Environment.TEST, false, URI("https://gateway.example"), "key"))

    private fun request(
        amount: MoneyRequest? = MoneyRequest(125_000, "HTG"),
        payer: PayerRequest? = PayerRequest("+50934567890"),
        description: String? = null,
        metadata: Map<String, Any?>? = null,
    ) = CreatePaymentRequest("INV-1", amount, "mock_alpha", payer, description, null, null, null, metadata)

    @Test
    fun `missing fields are reported as JSON pointers with the required code`() {
        val failure = assertFailsWith<OpenFspException> { mapper.toCommand(request(amount = MoneyRequest(null, "HTG"), payer = PayerRequest(null))) }
        assertEquals(ProblemType.MISSING_FIELD, failure.type)
        assertEquals(listOf("/amount/amount", "/payer/phone_number"), failure.errors.map { it.field })
        assertEquals(setOf(FieldErrorCode.REQUIRED), failure.errors.map { it.code }.toSet())
    }

    @Test
    fun `invalid fields carry a stable code and a JSON pointer`() {
        val failure =
            assertFailsWith<OpenFspException> {
                mapper.toCommand(request(amount = MoneyRequest(0, "HTG"), payer = PayerRequest("34567890"), description = "x".repeat(256)))
            }
        assertEquals(ProblemType.INVALID_FIELD, failure.type)
        assertEquals(
            listOf(
                FieldError("/amount/amount", FieldErrorCode.NOT_POSITIVE),
                FieldError("/payer/phone_number", FieldErrorCode.MALFORMED),
                FieldError("/description", FieldErrorCode.TOO_LONG),
            ),
            failure.errors.map { it.copy(detail = null) },
        )
    }

    @Test
    fun `metadata keys are escaped in the pointer`() {
        val failure = assertFailsWith<OpenFspException> { mapper.toCommand(request(metadata = mapOf("a/b~c" to "x", "count" to 3))) }
        assertEquals(listOf("/metadata/a~1b~0c", "/metadata/count"), failure.errors.map { it.field })
        assertEquals(listOf(FieldErrorCode.MALFORMED, FieldErrorCode.INVALID_TYPE), failure.errors.map { it.code })
    }
}
