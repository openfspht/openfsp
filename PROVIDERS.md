# Providers

The gateway provides adapters for the MonCash and NatCash APIs and a generic adapter for payment services exposing
the OpenFSP API natively. Declare each payment service under `openfsp.providers.<id>`, where `<id>` is its identifier
in the [provider registry](https://openfsp.org/docs/registry). Supply credentials through the environment or files in
`OPENFSP_SECRETS_DIR`; never store them in a file committed to the repository.

| | MonCash | NatCash |
|---|---|---|
| `type` | `moncash` | `natcash` |
| `base-url` | `https://sandbox.moncashbutton.digicelgroup.com/Api` (sandbox), `https://moncashbutton.digicelgroup.com/Api` (live) | `https://testmerchantpay.natcom.com.ht/api/online-payment` (UAT), `https://merchantpay.natcom.com.ht/api/online-payment` (live) |
| `secondary-url` | MonCash middleware: `https://sandbox.moncashbutton.digicelgroup.com/Moncash-middleware` | not needed |
| `credentials` | `client-id`, `client-secret` | `username`, `password`, `partner-code`, `private-key`, `function-code` |
| Capabilities | `payments`, `payments.lookup` | `payments`, `payments.lookup`; signed callbacks on a per-payment URL, used by the gateway only |
| Payer | sent to the MonCash page | optional `payer.phone_number`, sent as `msisdn` |
| `return_url` | required | not used |
| Locales | none | `fr`, `ht`, `en` |

Each payment service configuration accepts an optional `logo-url`, specified as an absolute URL or a path resolved
against `public-base-url`. The gateway publishes this value as `logo_url` in `/v1/capabilities` and `/admin/providers`.
An optional `display-name` defines the service's display label. The development profile uses it to identify the
simulated services as `MonCash (mock)` and `NatCash (mock)`.

## Native OpenFSP operators

For a payment service exposing the OpenFSP API natively, set `type: openfsp` (the default), a `base-url` pointing to
its issuer, and an `api-key` sent as a bearer token. The issuer URL serves `/.well-known/openfsp-configuration` and
`/v1`. The configured service identifier must match its registry identifier because the gateway transmits it in `provider`.

| | Native OpenFSP operator |
|---|---|
| Capabilities | retrieved from the payment service's `/v1/capabilities` and cached for the `max-age` in `Cache-Control` (five minutes if absent, at most one hour); limited to `payments` when retrieval is unavailable |
| Creation | `POST /v1/payments`, with `reference` set to the order identifier, `Idempotency-Key: create-<order id>`, and `callback_url` set to the callback URL when the payment service supports callbacks |
| Lookup | `GET /v1/payments?reference=<order id>`; an unrecognised status leaves the payment `pending` |
| Statement | `GET /v1/statements?provider=<id>&business_date=<day>` |
| Callbacks | OpenFSP events signed according to RFC 9421 and verified using `webhook_keys` from the service's discovery document; the key set is refreshed for an unknown `keyid` (at most every 10 seconds) and after one hour; both active and retired keys are accepted for verification; a verified event of an unknown type is acknowledged and changes nothing |
| Indeterminate creation | retrieved by order identifier, then retried with the same key (idempotence 7.2) |
| Operator error | a `4xx` response to the initial creation request produces `provider-error` with `effect: none` and no payment record, unless the service's problem specifies `effect: unknown`; a `504` or a timeout produces `provider-timeout`; a `5xx`, timeout or `4xx` following an unknown outcome retains `effect: unknown`; the returned problem's `detail` includes the final segment of the service's problem `type` and its `detail`, or its `title` when no detail is available |

For a payment service advertising `webhooks.per_payment_url` (API 5.1.8), the gateway supplies its own per-payment
`callback_url` to receive signed events. The gateway does not advertise this capability to its clients: a merchant
client supplying `callback_url` receives `capability-not-supported`. Payment `metadata` exposes the order identifier and the
payment service's transaction identifier as `gateway.order_id` and `gateway.provider_reference`, respectively.

## Simulated profile

The `simulated` profile configures `mock_openfsp`, `mock_moncash` and `mock_natcash` against the simulator at
`OPENFSP_SIMULATOR_URL` (default `http://127.0.0.1:8090`). It needs the `test` environment.

## Demo profile

The `demo` profile serves a public demonstration. It selects the `test` environment, accepts the published admin token
`demo-admin-token` and registers the published API key `ofsp_test_DemoShopKeyPublishedForTheSimulator01` on the default
merchant account. Combine it with `simulated`. Never use it for a gateway that holds real credentials.

## Sandbox profile

The `sandbox` profile configures `moncash` and `natcash` to use the providers' sandbox environments. Credentials are
read from `MONCASH_CLIENT_ID`, `MONCASH_CLIENT_SECRET`, `NATCASH_USERNAME`, `NATCASH_PASSWORD`, `NATCASH_PARTNER_CODE`,
`NATCASH_PRIVATE_KEY` and `NATCASH_FUNCTION_CODE`.

Define these credentials in `.env.sandbox` at the repository root, with one `NAME=value` entry per line. This file is
ignored by git. Run `make dev-sandbox` to load it when present; variables already exported in the shell are also
supported. If a required credential has no value, the gateway refuses to start and identifies the affected payment
service and missing credential.

Both sandbox integrations support payment creation and lookup. Completing a payment requires a sandbox wallet.
Receiving callbacks from the payment services requires an internet-accessible gateway.

## Simulated operators

The three simulated services described here are `mock_moncash`, `mock_natcash` and `mock_openfsp`. The first two expose
the MonCash and NatCash APIs, allowing the corresponding adapters to be tested without modification. The third exposes
a native OpenFSP interface under `/mock_openfsp`, covering discovery with its own Ed25519 `webhook_keys`, capabilities,
payments, lookup by reference, synchronisation, statements and signed events delivered to a per-payment callback URL.
The gateway accesses this service through the generic `openfsp` adapter, without simulator-specific code. The service
rejects an `http` callback URL with `invalid-field` unless its host is `localhost`, a loopback address or a host listed
in `openfsp.simulator.http-callback-hosts`.

Simulated service identifiers are fixed and published. The `mock_openfsp` key uses the `ofsp_test_` test-key format,
allowing the validator to assess `mock_openfsp` directly as a server. `mock_openfsp` also publishes a revoked key, an
expired key and a key belonging to a second principal with separate idempotency records. Revoked and expired keys are
rejected in the same way as unknown keys. These credentials allow revocation, expiry and principal tests to execute
without operator intervention.

| Operator | Adapter | Credentials (development only) |
|---|---|---|
| `mock_openfsp` | `openfsp` | `api-key`: `ofsp_test_MockOpenFspKeyPublishedForTheSimulator`; `second-principal-api-key`: `ofsp_test_MockOpenFspSecondPrincipalKeyForTheSimulator`; `revoked-api-key`: `ofsp_test_MockOpenFspRevokedKeyPublishedForTheSimulator`; `expired-api-key`: `ofsp_test_MockOpenFspExpiredKeyPublishedForTheSimulator` |
| `mock_moncash` | `moncash` | `client-id`, `client-secret` |
| `mock_natcash` | `natcash` | `username`, `password`, `partner-code`, `private-key`, `function-code` |

## Credentials per merchant account

A merchant account uses, for each MonCash or NatCash service, either the platform credentials configured here or its
own, set from the console. The choice is explicit: with its own credentials and no active revision, the account's new
payments are refused; they never fall back to the platform credentials. See [HARDENING.md](HARDENING.md), item 4.

A notification or payer return received at an account address (`/callbacks/<id>/accounts/<account>`,
`/return/<id>/accounts/<account>`) only gives the gateway the order to look up. The gateway finds the payment of that
account, then verifies the notification by a lookup with the credentials the payment recorded: its revision while it
is active or retired, or the platform credentials. A payment on a revoked revision is refused (`401`) and stays
pending for an attestation.

## MonCash return URL

MonCash redirects the payer to a single return URL configured in its business portal. Register
`<public-base-url>/return/moncash` as that URL. The gateway locates the payment by `orderId` or by `transactionId` through
`RetrieveTransactionPayment`, then redirects the payer to the payment's `return_url`.

## NatCash callbacks

Each NatCash payment has a dedicated callback URL, `/callbacks/natcash/<token>`. The gateway verifies the callback
signature using `function-code` before applying any state transition.

## Example

```
OPENFSP_PROVIDERS_MONCASH_TYPE=moncash
OPENFSP_PROVIDERS_MONCASH_BASE_URL=https://sandbox.moncashbutton.digicelgroup.com/Api
OPENFSP_PROVIDERS_MONCASH_SECONDARY_URL=https://sandbox.moncashbutton.digicelgroup.com/Moncash-middleware
OPENFSP_PROVIDERS_MONCASH_CREDENTIALS_CLIENT_ID=...
OPENFSP_PROVIDERS_MONCASH_CREDENTIALS_CLIENT_SECRET=...
```

Both adapters are based on provider documentation and earlier SDK implementations. Points requiring further sandbox
verification are listed in [To check in the sandbox](https://openfsp.org/en/docs/gateway#sandbox-checks).
