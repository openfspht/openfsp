<p align="center">
  <a href="https://openfsp.org">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://cdn.openfsp.org/logo/openfsp-logo-inverse.svg">
      <img src="https://cdn.openfsp.org/logo/openfsp-logo.svg" alt="OpenFSP" width="280">
    </picture>
  </a>
</p>

<p align="center"><b>OpenFSP SDK for TypeScript</b></p>

Accept MonCash, NatCash and every OpenFSP provider through one gateway. No runtime dependency; Node.js 20.19+, Bun and Deno.
The SDK holds your API key: run it on a server, never in a browser.

```sh
npm install @openfsp/sdk
```

## Getting started

```ts
import { Money, OpenFsp } from "@openfsp/sdk"

const openfsp = OpenFsp.fromEnv()

const payment = await openfsp.payments.create(
  { reference: "INV-184", amount: Money.htg(1250), provider: "moncash", payerPhoneNumber: "+50934567890" },
  { idempotencyKey: order.paymentKey },
)

if (payment.requiresRedirect) return redirect(payment.redirectUrl)
if (payment.awaitsPayerApproval) return show("Approve the payment on your phone")
```

`fromEnv` reads `OPENFSP_BASE_URL`, `OPENFSP_API_KEY` and, optionally, `OPENFSP_TIMEOUT_MS`, `OPENFSP_WEBHOOK_URL` and
`OPENFSP_ALLOW_PLAIN_HTTP`. `new OpenFsp({ baseUrl, apiKey })` takes the same settings in code.

## Payments

| Call | Returns |
|---|---|
| `payments.create(request, { idempotencyKey })` | the new `Payment` |
| `payments.get(id)` | the `Payment`, or throws `PaymentNotFoundError` |
| `payments.synchronize(id)` | the `Payment` after asking the provider |
| `payments.findByReference(reference)` | the `Payment`, or `undefined` |
| `providers()`, `provider(id)` | `Provider`: `displayName`, `supports(capability)`, `accepts(amount)` |

A `Payment` is immutable: `status`, `amount` (`Money`), `nextAction`, `isSuccessful`, `isTerminal`, `requiresRedirect`,
`redirectUrl`, dates as `Date`. `Money.htg(1250)` is 1 250 gourdes; `money.centimes` is the integer the protocol carries.

Persist the idempotency key with the order before calling `create`. The SDK resends with the same key on network failures,
`5xx` problems marked `retryable`, `rate-limited` and `idempotency-request-in-progress`, honouring `Retry-After`. When the
outcome stays unknown, it reads the payment back by reference and returns it if it matches.

## Webhooks

```ts
app.post("/openfsp/webhooks", async (request) => {
  const event = await openfsp.webhooks.verify(request)
  if (await events.seen(event.id)) return new Response(null, { status: 200 })
  if (event.payment.isSuccessful) await orders.markPaid(event.reference)
  return new Response(null, { status: 200 })
})
```

`verify` takes a fetch `Request` or `{ method, url, headers, body }` with the raw body. It checks the RFC 9421 Ed25519
signature with the gateway's keys, the digest, the validity window and the nonce, then returns a `PaymentEvent`. A bad
delivery throws `InvalidSignatureError`: answer `400`. Set `OPENFSP_WEBHOOK_URL` when a proxy changes the URL your
application sees, and pass a shared `nonceStore` when several instances receive deliveries.

## Errors

Every error extends `OpenFspError`.

| Error | When |
|---|---|
| `ConfigurationError` | invalid settings |
| `InvalidRequestError` | the request was refused before leaving |
| `TransportError` | network failure or timeout; the effect is unknown |
| `ApiError` | the gateway answered a problem: `code`, `status`, `retryable`, `effect`, `requestId`, `fieldErrors` |
| `PaymentNotFoundError` | an `ApiError` for an unknown payment |
| `InvalidSignatureError` | a webhook delivery failed verification |

## Testing

`@openfsp/sdk/testing` holds what the SDK's own tests use:

```ts
import { FakeTransport, jsonResponse, WebhookSigner, wirePayment } from "@openfsp/sdk/testing"

const transport = new FakeTransport(jsonResponse(201, wirePayment({ status: "succeeded" })))
const openfsp = new OpenFsp({ baseUrl: "https://gateway.test", apiKey: "test" }, { transport })

const signer = await WebhookSigner.generate()
const delivery = await signer.sign({ url: "https://shop.test/openfsp/webhooks" })
```

## Development

```sh
bun install
bun run typecheck && bun test && bun run build
bun run test:integration
```

The integration test needs `make simulator` at the repository root. The wire types are checked against
`conformance/schemas`, and webhook verification against the shared vectors in `conformance/vectors`.
