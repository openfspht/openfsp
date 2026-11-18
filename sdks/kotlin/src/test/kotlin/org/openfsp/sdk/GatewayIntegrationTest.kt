package org.openfsp.sdk

import org.junit.jupiter.api.Tag
import org.openfsp.sdk.model.Money
import org.openfsp.sdk.model.PaymentRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("integration")
class GatewayIntegrationTest {
    private val openfsp =
        OpenFsp(
            OpenFspConfig(
                System.getProperty("openfsp.gateway.url", "http://127.0.0.1:8080"),
                System.getProperty("openfsp.gateway.api-key") ?: System.getenv("OPENFSP_API_KEY") ?: error("set OPENFSP_API_KEY to a test key of the gateway"),
            ),
        )

    @Test
    fun `creates, reads and synchronizes a payment through the gateway`() {
        val provider = openfsp.providers().first { !it.returnUrlRequired && !it.payerRequired }.id
        val request = PaymentRequest("SDK-KT-${System.currentTimeMillis()}", Money.htg(1250), provider, description = "SDK interop")
        val key = Payments.newIdempotencyKey()

        val created = openfsp.payments.create(request, key)

        assertEquals(created.id, openfsp.payments.create(request, key).id)
        assertEquals(request.amount, created.amount)
        assertEquals(created.id, openfsp.payments.get(created.id).id)
        assertEquals(created.id, openfsp.payments.findByReference(request.reference)?.id)
        assertEquals(created.id, openfsp.payments.synchronize(created.id).id)
        assertTrue(openfsp.configuration().webhookKeys.isNotEmpty())
        val reused = runCatching { openfsp.payments.create(request.copy(amount = Money.htg(1300)), key) }.exceptionOrNull()
        assertEquals("idempotency-key-reused", (reused as ApiException).code)
    }
}
