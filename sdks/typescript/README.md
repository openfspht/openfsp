<p align="center">
  <a href="https://openfsp.org">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://cdn.openfsp.org/logo/openfsp-logo-inverse.svg">
      <img src="https://cdn.openfsp.org/logo/openfsp-logo.svg" alt="OpenFSP" width="280">
    </picture>
  </a>
</p>

<p align="center"><b>OpenFSP SDK for TypeScript</b></p>

Integrate payment services exposed by an OpenFSP server, including MonCash and NatCash where supported by that server.
The SDK has no runtime dependencies and supports Node.js 20.19+, Bun and Deno. It uses a secret API key and must run
on a server, never in a browser.

```sh
npm install @openfsp/sdk
```

## Getting started

```ts
import { Money, OpenFsp } from "@openfsp/sdk"

const openfsp = OpenFsp.fromEnv()

const payment = await openfsp.payments.create(
  {
    reference: "INV-184",
    amount: Money.htg(1250),
    provider: "moncash",
    payerPhoneNumber: "+50934567890",
  },
  { idempotencyKey: order.paymentKey }
)

if (payment.requiresRedirect) return redirect(payment.redirectUrl)
if (payment.awaitsPayerApproval) return show("Approve the payment on your phone")
```

`fromEnv` reads `OPENFSP_BASE_URL`, `OPENFSP_API_KEY` and, optionally, `OPENFSP_TIMEOUT_MS`, `OPENFSP_WEBHOOK_URL` and
`OPENFSP_ALLOW_PLAIN_HTTP`. `new OpenFsp({ baseUrl, apiKey })` takes the same settings in code.

## Payments

| Call                                           | Returns                                                                                   |
| ---------------------------------------------- | ----------------------------------------------------------------------------------------- |
| `payments.create(request, { idempotencyKey })` | the new `Payment`                                                                         |
| `payments.get(id)`                             | the `Payment`, or throws `PaymentNotFoundError`                                           |
| `payments.synchronize(id)`                     | the `Payment` after synchronisation with the payment service                              |
| `payments.findByReference(reference)`          | the `Payment`, or `undefined`                                                             |
| `providers()`, `provider(id)`                  | `Provider`: `displayName`, `supports(capability)`, `accepts(amount)`; kept for 60 seconds |
| `configuration()`                              | `ServerConfiguration`: the discovery document, endpoints optional                         |

A `Payment` is immutable and exposes `status`, `amount` (`Money`), `nextAction`, `isSuccessful`, `isTerminal`,
`requiresRedirect` and `redirectUrl`, with dates represented as `Date`. `Money.htg(1250)` represents 1 250 gourdes;
`money.centimes` is the integer amount transmitted by the protocol.

The idempotency key is required: generate it once with `newIdempotencyKey()` and persist it with the order before
calling `create`, so a retry after a crash reuses it. The SDK retries with the same key after network
failures, `5xx` problems marked `retryable`, `502`, `503` and `504` answers that are not problem documents (a proxy
page), and the `rate-limited` and `idempotency-request-in-progress` problems. Retries
respect `Retry-After`. If the outcome remains unknown, the SDK retrieves the payment by reference and returns it when
it matches the request.

## Webhooks

```ts
app.post("/openfsp/webhooks", async (request) => {
  let event: PaymentEvent
  try {
    event = await openfsp.webhooks.verify(request)
  } catch (error) {
    if (error instanceof InvalidSignatureError) return new Response(null, { status: 400 })
    if (error instanceof WebhookKeysUnavailableError) return new Response(null, { status: 503 })
    throw error
  }
  if (await events.seen(event.id)) return new Response(null, { status: 200 })
  if (event.payment.isSuccessful) await orders.markPaid(event.reference)
  return new Response(null, { status: 200 })
})
```

`verify` accepts a Fetch API `Request` or `{ method, url, headers, body }` containing the raw body. It validates the
RFC 9421 Ed25519 signature using the server's keys, checks the digest, validity window and nonce, and returns a
`PaymentEvent` whose `type` is one of `PAYMENT_EVENT_TYPES`. A delivery that fails verification, or a signed body that
is not a supported event, raises `InvalidSignatureError`; respond with `400`. When the server's keys cannot be loaded,
`verify` raises `WebhookKeysUnavailableError`; respond with `503` so the server delivers again. Set
`OPENFSP_WEBHOOK_URL` if a proxy changes the URL visible to the application. Provide a shared `nonceStore` when multiple
instances receive deliveries.

## Errors

Every error extends `OpenFspError`.

| Error                         | When                                                                                                        |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `ConfigurationError`          | invalid settings                                                                                            |
| `InvalidRequestError`         | the request was rejected before transmission                                                                |
| `TransportError`              | network failure or timeout; the effect is unknown                                                           |
| `ApiError`                    | the server returned a problem response: `code`, `status`, `retryable`, `effect`, `requestId`, `fieldErrors` |
| `PaymentNotFoundError`        | an `ApiError` for an unknown payment                                                                        |
| `InvalidSignatureError`       | a webhook delivery failed verification or carries no supported event                                        |
| `WebhookKeysUnavailableError` | the webhook keys could not be loaded from the server                                                        |

## Testing

`@openfsp/sdk/testing` provides the test utilities used by the SDK itself:

```ts
import { FakeTransport, jsonResponse, WebhookSigner, wirePayment } from "@openfsp/sdk/testing"

const transport = new FakeTransport(jsonResponse(201, wirePayment({ status: "succeeded" })))
const openfsp = new OpenFsp({ baseUrl: "https://server.test", apiKey: "test" }, { transport })

const signer = await WebhookSigner.generate()
const delivery = await signer.sign({ url: "https://shop.test/openfsp/webhooks" })
```

`wirePayment` and `wireEvent` build wire documents that follow the specification: a pending payment carries a
`payer_approval` next action and a `payment.created` event has no previous status.

## Development

```sh
bun install
bun run typecheck && bun test && bun run build
bun run test:integration
```

Integration testing requires a simulator started with `make simulator` from the repository root. Wire types are
validated against `conformance/schemas`, and webhook verification uses the shared test vectors in `conformance/vectors`.
