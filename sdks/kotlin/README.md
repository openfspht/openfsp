<p align="center">
  <a href="https://openfsp.org">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://cdn.openfsp.org/logo/openfsp-logo-inverse.svg">
      <img src="https://cdn.openfsp.org/logo/openfsp-logo.svg" alt="OpenFSP" width="280">
    </picture>
  </a>
</p>

<p align="center"><b>OpenFSP SDK for Kotlin and Java</b></p>

Accept MonCash, NatCash and every OpenFSP provider through one gateway. Java 17 or later; the only runtime dependencies are
`openfsp-protocol`, Jackson 3 and the Kotlin standard library.

```kotlin
dependencies {
    implementation("org.openfsp:openfsp-sdk:0.1.0")
    testImplementation("org.openfsp:openfsp-sdk-testing:0.1.0")
}
```

Published on Maven Central and GitHub Packages (`https://maven.pkg.github.com/openfspht/openfsp`).

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
OpenFsp openfsp = new OpenFsp(OpenFspConfig.builder().baseUrl("https://gateway.example").apiKey(apiKey).build());

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
| `payments.synchronize(id)` | the `Payment` after asking the provider |
| `payments.findByReference(reference)` | the `Payment`, or `null` |
| `providers()`, `provider(id)` | `Provider`: `displayName`, `supports(capability)`, `accepts(amount)` |

A `Payment` is immutable: `status`, `amount` (`Money`), `nextAction` (`Redirect`, `PayerApproval`, `None`), `isSuccessful`,
`isTerminal`, `requiresRedirect`, `redirectUrl`, dates as `Instant`. `Money.htg(1250)` is 1 250 gourdes; `money.centimes` is
the integer the protocol carries.

Persist the idempotency key with the order before calling `create`. The SDK resends with the same key on network failures,
`5xx` problems marked `retryable`, `rate-limited` and `idempotency-request-in-progress`, honouring `Retry-After`. When the
outcome stays unknown, it reads the payment back by reference and returns it if it matches.

## Webhooks

```kotlin
@PostMapping("/openfsp/webhooks")
fun receive(request: HttpServletRequest, @RequestBody body: ByteArray): ResponseEntity<Unit> {
    val headers = request.headerNames.asSequence().associateWith(request::getHeader)
    val event = try {
        openfsp.webhooks.verify(WebhookDelivery(request.method, request.requestURL.toString(), headers, body))
    } catch (rejected: InvalidSignatureException) {
        return ResponseEntity.badRequest().build()
    }
    if (events.firstTime(event.id) && event.payment.isSuccessful) orders.markPaid(event.reference)
    return ResponseEntity.ok().build()
}
```

`verify` checks the RFC 9421 Ed25519 signature with the gateway's keys, the digest, the validity window and the nonce, then
returns a `PaymentEvent`. Pass the body exactly as received. Set `OPENFSP_WEBHOOK_URL` when a proxy changes the URL your
application sees, and pass a shared `NonceStore` to `OpenFsp` when several instances receive deliveries.

## Errors

Every exception extends `OpenFspException`.

| Exception | When |
|---|---|
| `ConfigurationException` | invalid settings |
| `InvalidRequestException` | the request was refused before leaving |
| `TransportException` | network failure or timeout; the effect is unknown |
| `ApiException` | the gateway answered a problem: `code`, `status`, `retryable`, `effect`, `requestId`, `fieldErrors` |
| `PaymentNotFoundException` | an `ApiException` for an unknown payment |
| `InvalidSignatureException` | a webhook delivery failed verification |

## Testing

`openfsp-sdk-testing` holds what the SDK's own tests use:

```kotlin
val transport = FakeTransport(Responses.json(201, Wire.payment(status = "succeeded")))
val openfsp = OpenFsp(OpenFspConfig("https://gateway.test", "test"), transport, FakeClock())

val signer = WebhookSigner.generate()
val webhooks = Webhooks.withKeys(listOf(signer.jwk))
val event = webhooks.verify(signer.sign("https://shop.test/openfsp/webhooks"))
```

## Development

```sh
./gradlew build
./gradlew integrationTest -PsimulatorUrl=http://127.0.0.1:8090/mock_openfsp
```

The integration test needs `make simulator` at the repository root. Webhook verification is checked against the shared
vectors in `conformance/vectors`, which the TypeScript SDK verifies too.
