# OpenAPI description

`openapi.yaml` is the OpenAPI 3.1 description of the OpenFSP HTTP API: discovery, payments, statements and webhook deliveries. The SDKs in [`../sdks`](../sdks) implement it.

Payload shapes are not defined here: every schema is a `$ref` to the JSON Schemas in [`../conformance/schemas`](../conformance/schemas), which the conformance suite also validates against. Change a shape there, never by copying it into this file.

Rule citations such as `payments-api#5.1` refer to the specification at https://openfsp.org/docs/spec.

Lint from the repository root (also part of `make lint` and CI):

```sh
REDOCLY_TELEMETRY=off bunx @redocly/cli@2.72.1 lint --config openapi/redocly.yaml openapi/openapi.yaml
```
