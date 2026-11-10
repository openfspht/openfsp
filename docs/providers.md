# Providers

I ship two dialect adapters, MonCash and NatCash, and one adapter for any operator that speaks OpenFSP natively. Each provider is declared under
`openfsp.providers.<id>`, where `<id>` is the identifier from the
[provider registry](https://github.com/openfspht/openfsp/blob/main/registries/providers.md). Credentials come from the
environment or from files in `OPENFSP_SECRETS_DIR`, never from a committed file.

| | MonCash | NatCash |
|---|---|---|
| `type` | `moncash` | `natcash` |
| `base-url` | `https://sandbox.moncashbutton.digicelgroup.com/Api` (sandbox), `https://moncashbutton.digicelgroup.com/Api` (live) | `https://testmerchantpay.natcom.com.ht/api/online-payment` (UAT), `https://merchantpay.natcom.com.ht/api/online-payment` (live) |
| `secondary-url` | MonCash middleware: `https://sandbox.moncashbutton.digicelgroup.com/Moncash-middleware` | not needed |
| `credentials` | `client-id`, `client-secret` | `username`, `password`, `partner-code`, `private-key`, `function-code` |
| Capabilities | `payments`, `payments.lookup` | `payments`, `payments.lookup`, `webhooks.verify`, `webhooks.per_payment_url` |
| Payer | sent to the MonCash page | optional `payer.phone_number`, sent as `msisdn` |
| `return_url` | required | not used |
| Locales | none | `fr`, `ht`, `en` |

Any provider takes an optional `logo-url`, an absolute URL or a path resolved against `public-base-url`. The gateway
announces it as `logo_url` in `/v1/capabilities` and `/admin/providers`.
Any provider also takes an optional `display-name`; the development profile uses it to mark the simulated operators,
`MonCash (mock)` and `NatCash (mock)`.

## Native OpenFSP operators

An operator that exposes the OpenFSP API itself takes `type: openfsp` (the default), a `base-url` pointing at its issuer
(the URL that serves `/.well-known/openfsp-configuration` and `/v1`), and an `api-key` sent as a bearer token. The
provider id must be the operator's registry id, since the gateway sends it as `provider`.

| | Native OpenFSP operator |
|---|---|
| Capabilities | read from the operator's `/v1/capabilities`, kept for the `max-age` of its `Cache-Control` (five minutes without one, an hour at most); only `payments` while it cannot be read |
| Creation | `POST /v1/payments`, `reference` set to the order id, `Idempotency-Key: create-<order id>`, `callback_url` set to the callback URL when the operator calls back |
| Lookup | `GET /v1/payments?reference=<order id>` |
| Statement | `GET /v1/statements?provider=<id>&business_date=<day>` |
| Callbacks | OpenFSP events signed per RFC 9421, verified with the `webhook_keys` of the operator's discovery document; the key set is replaced on an unknown `keyid` (at most every 10 seconds) and after an hour, active and retired keys both verify |
| Indeterminate creation | looked up by order id, then resent with the same key (idempotence 7.2) |

`callback_url` is the creation member reserved to gateway-to-operator calls (API 5.1.8): the operator delivers the
signed events of the payment there. A merchant client that sends it gets `unknown-field`.

## Sandbox profile

The `sandbox` profile adds `moncash` and `natcash` on the operators' sandboxes. It reads the credentials from
`MONCASH_CLIENT_ID`, `MONCASH_CLIENT_SECRET`, `NATCASH_USERNAME`, `NATCASH_PASSWORD`, `NATCASH_PARTNER_CODE`,
`NATCASH_PRIVATE_KEY` and `NATCASH_FUNCTION_CODE`.

```
set -a; . /path/to/sandbox.env; set +a
make dev-sandbox
```

Payment creation and lookup work on both sandboxes. Completing a payment needs a sandbox wallet, and the operator
callbacks need a gateway reachable from the Internet.

## Simulated operators

The simulator serves three operators. `mock_moncash` and `mock_natcash` speak the MonCash and NatCash APIs, so the
real adapters run against them unchanged. `mock_openfsp` is a native OpenFSP operator under `/mock_openfsp`: discovery
with its own Ed25519 `webhook_keys`, capabilities, payments, lookup by reference, synchronization, statements, and
signed events on the per-payment callback URL. The gateway reaches it with the generic `openfsp` adapter, with no code
specific to the simulator.

| Operator | Adapter | Credentials (development only) |
|---|---|---|
| `mock_openfsp` | `openfsp` | `api-key` |
| `mock_moncash` | `moncash` | `client-id`, `client-secret` |
| `mock_natcash` | `natcash` | `username`, `password`, `partner-code`, `private-key`, `function-code` |

## MonCash return URL

MonCash returns the payer to one URL declared in the business portal. Declare
`<public-base-url>/return/moncash` there: the gateway finds the payment by `orderId`, or by `transactionId` through
`RetrieveTransactionPayment`, then redirects the payer to the `return_url` of the payment.

## NatCash callbacks

Each NatCash payment gets its own callback URL, `/callbacks/natcash/<token>`. The gateway verifies the signature with
the `function-code` before any transition.

## Example

```
OPENFSP_PROVIDERS_MONCASH_TYPE=moncash
OPENFSP_PROVIDERS_MONCASH_BASE_URL=https://sandbox.moncashbutton.digicelgroup.com/Api
OPENFSP_PROVIDERS_MONCASH_SECONDARY_URL=https://sandbox.moncashbutton.digicelgroup.com/Moncash-middleware
OPENFSP_PROVIDERS_MONCASH_CREDENTIALS_CLIENT_ID=...
OPENFSP_PROVIDERS_MONCASH_CREDENTIALS_CLIENT_SECRET=...
```

Both adapters follow the operator documentation and my earlier SDKs; the points still to confirm in a sandbox are
listed in the specification, [architecture 8.5](https://github.com/openfspht/adrs/blob/main/spec/architecture.md).
