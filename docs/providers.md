# Providers

I ship two real adapters, MonCash and NatCash, next to the simulated operators. Each provider is declared under
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
