<p align="center">
  <a href="https://openfsp.org">
    <picture>
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
    implementation("org.openfsp:openfsp-sdk:0.5.0") // x-release-please-version
    testImplementation("org.openfsp:openfsp-sdk-testing:0.5.0") // x-release-please-version
}
```

The SDK is distributed through [Maven Central](https://central.sonatype.com/artifact/org.openfsp/openfsp-sdk).

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
| `payments.findByReference(reference)` | the `Payment`, or `null` |
| `providers()`, `provider(id)` | `Provider`: `displayName`, `supports(capability)`, `accepts(amount)`; kept for the lifetime the server announces, 60 seconds by default |
| `statements.forDay(provider, businessDate)` | a `StatementPage` of the successful transactions of that business date |
| `statements.between(provider, from, to)` | a `StatementPage` over a range of at most 31 days, both ends included |
| `configuration()` | `ServerConfiguration`: the discovery document; endpoints the server omits are `null` |

A `Payment` is immutable and exposes `status`, `amount` (`Money`), `nextAction` (`Redirect`, `PayerApproval`, `None`),
`isSuccessful`, `isTerminal`, `requiresRedirect` and `redirectUrl`, with dates represented as `Instant`.
`Money.htg(1250)` represents 1 250 gourdes; `money.centimes` is the integer amount transmitted by the protocol.

A `StatementPage` holds `entries`, sorted by ascending `bookedAt`, and `nextCursor`. Both statement calls accept an
optional `limit` (1 to 100, 50 by default) and `cursor`; pass `nextCursor` back while `hasMore` is true. A
`StatementEntry` exposes `provider`, `businessDate`, `bookedAt`, `amount` and, when the server reports them,
`fee`, `paymentId` and `reference`. Like `Payment.fee`, `fee` carries an `amount` and a `bearer` (`MERCHANT` or
`PAYER`); `null` means the fee is unknown, never zero.

The idempotency key is required: generate it once with `Payments.newIdempotencyKey()` and persist it with the order
before calling `create`, so a retry after a crash reuses it. The SDK retries with the same key after network
failures, `5xx` problems marked `retryable`, `502`, `503` and `504` answers that are not problem documents (a proxy
page), and the `rate-limited` and `idempotency-request-in-progress` problems. Retries
respect `Retry-After`. If the outcome remains unknown, the SDK retrieves the payment by reference and returns it when
it matches the request. Build the configuration with
`recoverLostAnswers(false)` to receive the failure instead and recover in your own way. A payment whose status the SDK
does not know raises `UnknownPaymentStatusException`, which names the payment and infers no state.

## Webhooks

```kotlin
@PostMapping("/openfsp/webhooks")
fun receive(request: HttpServletRequest, @RequestBody body: ByteArray): ResponseEntity<Unit> {
    val headers = request.headerNames.asSequence().associateWith(request::getHeader)
    val event = try {
        openfsp.webhooks.verify(WebhookDelivery(request.method, request.requestURL.toString(), headers, body))
    } catch (unknown: UnknownEventTypeException) {
        return ResponseEntity.ok().build()
    } catch (rejected: InvalidSignatureException) {
        return ResponseEntity.badRequest().build()
    } catch (unavailable: WebhookKeysUnavailableException) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
    }
    if (events.firstTime(event.id) && event.payment.isSuccessful) orders.markPaid(event.reference)
    return ResponseEntity.ok().build()
}
```

`verify` validates the RFC 9421 signature (`ed25519`, or `ecdsa-p256-sha256` for an ES256 key) using the server's keys,
checks the digest, validity window and nonce, and returns a `PaymentEvent` whose `type` is a `PaymentEventType`
(`CREATED`, `SUCCEEDED`, `FAILED`, `EXPIRED`, `CANCELED`). A verified event of another type throws
`UnknownEventTypeException` with its `eventId` and `eventType`: acknowledge it with `2xx`. It throws
`InvalidSignatureException` for a delivery to reject, including a signed payment event the SDK cannot read, and
`WebhookKeysUnavailableException` when the server's keys cannot be loaded: answer `503` so the server delivers again.
Supply the body exactly as received. Set `OPENFSP_WEBHOOK_URL` if a proxy changes the URL visible to the application.

The default `InMemoryNonceStore` only sees the deliveries of its own JVM. When several instances receive deliveries,
pass `OpenFsp` a `NonceStore` they share. `reserve(nonce, until)` is called once the signature verifies; it must record
the nonce until `until` and return `false` if the nonce is already recorded, atomically. With PostgreSQL:

```kotlin
class JdbcNonceStore(private val jdbc: JdbcTemplate) : NonceStore {
    override fun reserve(nonce: String, until: Instant): Boolean =
        jdbc.update(
            "insert into webhook_nonces (nonce, expires_at) values (?, ?) on conflict (nonce) do nothing",
            nonce, Timestamp.from(until),
        ) == 1

    override fun release(nonce: String) {
        jdbc.update("delete from webhook_nonces where nonce = ?", nonce)
    }
}

val openfsp = OpenFsp(OpenFspConfig.fromEnv(), nonceStore = JdbcNonceStore(jdbc))
```

Delete expired rows on a schedule.

## Errors

Every exception extends `OpenFspException`.

| Exception | When |
|---|---|
| `ConfigurationException` | invalid settings |
| `InvalidRequestException` | the request was rejected before transmission |
| `TransportException` | network failure or timeout; the effect is unknown |
| `ApiException` | the server returned a problem response: `code`, `status`, `retryable`, `effect`, `requestId`, `fieldErrors`, `instance`, and `minAmount`, `maxAmount`, `amountStep` for `amount-out-of-range` |
| `PaymentNotFoundException` | an `ApiException` for an unknown payment |
| `InvalidSignatureException` | a webhook delivery failed verification or carries a payment event the SDK cannot read |
| `UnknownEventTypeException` | a verified webhook delivery carries an event type the SDK does not know: acknowledge it |
| `WebhookKeysUnavailableException` | the webhook keys could not be loaded from the server |

## Testing

`openfsp-sdk-testing` provides the test utilities used by the SDK itself:

```kotlin
val clock = FakeClock()
val transport = FakeTransport(Responses.json(201, Wire.payment(status = "succeeded")))
    .enqueueFailure(TransportException("connection reset"))
val openfsp = OpenFsp(OpenFspConfig("https://server.test", "test"), transport, clock)

val signer = WebhookSigner.generate()
val webhooks = Webhooks.withKeys(listOf(signer.key), clock = clock)
val event = webhooks.verify(signer.sign("https://shop.test/openfsp/webhooks", clock = clock))
```

`Wire.payment`, `Wire.event`, `Wire.statement` and `Wire.configuration` build wire documents that follow the specification: a pending
payment carries a `payer_approval` next action and a `payment.created` event has no previous status.

## Development

The SDK is built with the backend, from the `backend` directory:

```sh
./gradlew :sdk:build :sdk-testing:build
OPENFSP_API_KEY=ofsp_test_... ./gradlew :sdk:integrationTest -PgatewayUrl=http://127.0.0.1:8080
```

Integration testing requires a gateway started with `make dev` from the repository root and one of its test keys.
Webhook verification uses the shared test vectors in `conformance/vectors`, also used by the TypeScript SDK.
