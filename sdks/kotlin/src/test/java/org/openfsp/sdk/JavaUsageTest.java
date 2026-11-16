package org.openfsp.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openfsp.sdk.http.RetryPolicy;
import org.openfsp.sdk.model.Money;
import org.openfsp.sdk.model.Payment;
import org.openfsp.sdk.model.PaymentRequest;
import org.openfsp.sdk.testing.FakeTransport;
import org.openfsp.sdk.testing.Responses;
import org.openfsp.sdk.testing.Wire;
import org.openfsp.sdk.webhooks.WebhookDelivery;
import org.openfsp.sdk.webhooks.Webhooks;

class JavaUsageTest {
    @Test
    void readsNaturallyFromJava() {
        OpenFspConfig config = OpenFspConfig.builder()
                .baseUrl("https://server.example")
                .apiKey("ofsp_test_key")
                .timeout(Duration.ofSeconds(10))
                .retryPolicy(new RetryPolicy(5))
                .build();
        OpenFsp openfsp = new OpenFsp(config, new FakeTransport(Responses.json(201, Wire.payment())));
        PaymentRequest request = PaymentRequest.builder("INV-184", Money.htg(1250), "moncash")
                .payerPhoneNumber("+50934567890")
                .metadata(Map.of("order", "184"))
                .build();

        Payment payment = openfsp.payments().create(request, Payments.newIdempotencyKey());

        assertTrue(payment.isPending());
        assertEquals(Money.htg("1250.00"), payment.getAmount());
        assertEquals("OpenFsp(https://server.example/)", openfsp.toString());
        WebhookDelivery unsigned = new WebhookDelivery("POST", "https://shop.example/hook", Map.of(), new byte[0]);
        assertThrows(InvalidSignatureException.class, () -> Webhooks.withKeys(List.of()).verify(unsigned));
    }
}
