<p align="center">
  <a href="https://openfsp.org">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://cdn.openfsp.org/logo/openfsp-logo-inverse.svg">
      <img src="https://cdn.openfsp.org/logo/openfsp-logo.svg" alt="OpenFSP" width="280">
    </picture>
  </a>
</p>

<p align="center"><b>OpenFSP SDK for Kotlin and Java</b></p>

Integrate payment services exposed by an OpenFSP server, including MonCash and NatCash where supported by that server.
The SDK requires Java 17 or later. Its runtime dependencies are limited to `openfsp-protocol`, Jackson 3 and the Kotlin
standard library.

```kotlin
dependencies {
    implementation("org.openfsp:openfsp-sdk:0.1.0")
    testImplementation("org.openfsp:openfsp-sdk-testing:0.1.0")
}
```

The SDK is distributed through Maven Central and GitHub Packages (`https://maven.pkg.github.com/openfspht/openfsp`).

## Getting started

```kotlin
val openfsp = OpenFsp.fromEnv()

val payment = openfsp.payments.create(
    PaymentRequest(reference = "INV-184", amount = Money.htg(1250), provider = "moncash", payerPhoneNumber = "+50934567890"),
    idempotencyKey = order.paymentKey,
)

when {
    payment.requiresRedirect -> redirect(payment.redirectUrl)
    payment.awaitsPayerApproval -> show("Approve the payment on your phone")
}
```

```java
OpenFsp openfsp = new OpenFsp(OpenFspConfig.builder().baseUrl("https://server.example").apiKey(apiKey).build());

PaymentRequest request = PaymentRequest.builder("INV-184", Money.htg(1250), "moncash").payerPhoneNumber("+50934567890").build();
Payment payment = openfsp.payments().create(request, order.getPaymentKey());
```

`fromEnv` reads `OPENFSP_BASE_URL`, `OPENFSP_API_KEY` and, optionally, `OPENFSP_TIMEOUT_MS`, `OPENFSP_WEBHOOK_URL` and
`OPENFSP_ALLOW_PLAIN_HTTP`.

## Payments

| Call | Returns |
|---|---|
| `payments.create(request, idempotencyKey)` | the new `Payment` |
| `payments.get(id)` | the `Payment`, or throws `PaymentNotFoundException` |
| `payments.synchronize(id)` | the `Payment` after synchronisation with the payment service |
| `payments.findByReference(reference)` | the `Payment`, or `null` |
| `providers()`, `provider(id)` | `Provider`: `displayName`, `supports(capability)`, `accepts(amount)`; kept for 60 seconds |
| `configuration()` | `ServerConfiguration`: the discovery document; endpoints the server omits are `null` |

A `Payment` is immutable and exposes `status`, `amount` (`Money`), `nextAction` (`Redirect`, `PayerApproval`, `None`),
`isSuccessful`, `isTerminal`, `requiresRedirect` and `redirectUrl`, with dates represented as `Instant`.
`Money.htg(1250)` represents 1 250 gourdes; `money.centimes` is the integer amount transmitted by the protocol.

The idempotency key is required: generate it once with `Payments.newIdempotencyKey()` and persist it with the order
before calling `create`, so a retry after a crash reuses it. The SDK retries with the same key after network
failures, `5xx` problems marked `retryable`, `502`, `503` and `504` answers that are not problem documents (a proxy
page), and the `rate-limited` and `idempotency-request-in-progress` problems. Retries
respect `Retry-After`. If the outcome remains unknown, the SDK retrieves the payment by reference and returns it when
it matches the request.

## Webhooks

```kotlin
@PostMapping("/openfsp/webhooks")
fun receive(request: HttpServletRequest, @RequestBody body: ByteArray): ResponseEntity<Unit> {
    val headers = request.headerNames.asSequence().associateWith(request::getHeader)
    val event = try {
        openfsp.webhooks.verify(WebhookDelivery(request.method, request.requestURL.toString(), headers, body))
    } catch (rejected: InvalidSignatureException) {
        return ResponseEntity.badRequest().build()
    } catch (unavailable: WebhookKeysUnavailableException) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
    }
    if (events.firstTime(event.id) && event.payment.isSuccessful) orders.markPaid(event.reference)
    return ResponseEntity.ok().build()
}
```

`verify` validates the RFC 9421 Ed25519 signature using the server's keys, checks the digest, validity window and nonce,
and returns a `PaymentEvent`. It throws `InvalidSignatureException` for a delivery to reject, including a signed body
that is not a supported event, and `WebhookKeysUnavailableException` when the server's keys cannot be loaded: answer
`503` so the server delivers again. Supply the body exactly as received. Set `OPENFSP_WEBHOOK_URL` if a proxy changes the URL
visible to the application. When multiple instances receive deliveries, provide a shared `NonceStore` to `OpenFsp`.

## Errors

Every exception extends `OpenFspException`.

| Exception | When |
|---|---|
| `ConfigurationException` | invalid settings |
| `InvalidRequestException` | the request was rejected before transmission |
| `TransportException` | network failure or timeout; the effect is unknown |
| `ApiException` | the server returned a problem response: `code`, `status`, `retryable`, `effect`, `requestId`, `fieldErrors` |
| `PaymentNotFoundException` | an `ApiException` for an unknown payment |
| `InvalidSignatureException` | a webhook delivery failed verification or carries no supported event |
| `WebhookKeysUnavailableException` | the webhook keys could not be loaded from the server |

## Testing

`openfsp-sdk-testing` provides the test utilities used by the SDK itself:

```kotlin
val transport = FakeTransport(Responses.json(201, Wire.payment(status = "succeeded")))
val openfsp = OpenFsp(OpenFspConfig("https://server.test", "test"), transport, FakeClock())

val signer = WebhookSigner.generate()
val webhooks = Webhooks.withKeys(listOf(signer.key))
val event = webhooks.verify(signer.sign("https://shop.test/openfsp/webhooks"))
```

## Development

```sh
./gradlew build
./gradlew integrationTest -PsimulatorUrl=http://127.0.0.1:8090/mock_openfsp
```

Integration testing requires a simulator started with `make simulator` from the repository root. Webhook verification
uses the shared test vectors in `conformance/vectors`, also used by the TypeScript SDK.
