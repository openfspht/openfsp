# OpenAPI description

`openapi.yaml` describes the OpenFSP HTTP API in OpenAPI 3.1 format, covering discovery, payments, statements and webhook deliveries. The SDKs in [`../sdks`](../sdks) implement this contract.

Payload structures are defined by the JSON Schemas in [`../conformance/schemas`](../conformance/schemas) and referenced through `$ref`. The conformance suite validates payloads against these same schemas. Modify payload definitions at that source; do not duplicate them in the OpenAPI description.

References such as `payments-api#5.1` identify clauses in the specification at https://openfsp.org/docs/spec.

Run the linter from the repository root. This check is also included in `make check` and CI:

```sh
REDOCLY_TELEMETRY=off bunx @redocly/cli@2.80.0 lint --config openapi/redocly.yaml openapi/openapi.yaml
```
