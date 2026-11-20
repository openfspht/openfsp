# Conformance

The French specification at openfsp.org is canonical; the English pages carry the same rule numbers.

## Traceability

`traceability.yaml` indexes every numbered clause of the specification in specification order, using the
`chapter#number` format. Each entry names the responsible role (`server`, `client`, `operator`, `suite` or
`informative`) and, for server clauses, the test target (`server`). Verification is mapped to requirements in
`requirements.yaml` and their scenarios, repository unit tests, or review where automated observation is not possible.
A testable server obligation without a test is recorded as a `gap`. `TraceabilityTest` in
`backend/validator/infrastructure` validates enumeration values, referenced requirements and test files, checks that
every requirement is referenced, and compares the indexed clauses with the specification found at
`OPENFSP_SPECIFICATION` or in an openfsp.org checkout next to this repository. In CI the specification is checked out
and the comparison is mandatory.

## Running the suite

The suite evaluates a server; the client target is not offered yet. `{{provider}}` identifies a payment service the
server advertises in `/v1/capabilities`. The engine selects the first service that advertises the requirement's
profile and all capabilities in the scenario's `with-capabilities`, while advertising none of its
`without-capabilities`. `--provider` gives priority to a service that meets these conditions. A scenario is not
applicable if no advertised service qualifies.

The server prepares the run (conformance 4.8): the suite posts its webhook receiver to `conformance_endpoint` and
receives a test key, the inputs the server can produce and `control_endpoint`, which `schemas/fixtures.json` requires.
A scenario that needs a payment outcome chooses it in a `control` step through that interface (conformance 4.9):
`declare` sets the scenario of a `reference`, `resolve` gives a pending payment its outcome and `callback` triggers its
held event. Without a control interface, or when the server refuses a scenario with
422, the scenario fails; there is no assisted path for outcomes. The report then carries `outcomes: suite`.

`make validate` runs the suite against the implementation at `TARGET`, the gateway at `http://127.0.0.1:8080` by
default; `make validate TARGET=http://127.0.0.1:8090/mock_openfsp` runs it against the simulated OpenFSP operator.
`REPORT` and `WEBHOOK_PORT` set the report file and the port of the webhook receiver. The command exits 0 when Core
passes, 1 when it fails and 2 when it is incomplete. A key given with `--api-key` must be a test key and comes with
`--control-endpoint`, a URL on the origin of `--base-url`; inputs given with `--input` take precedence over those of the
server. A scenario is assisted only when it applies and still lacks an input that neither the server nor the operator
provided.

The `webhooks.emit` scenarios inspect the events received by the webhook receiver of the run. Configure the CLI receiver
with `--webhook-port`, plus `--webhook-bind` and `--webhook-url` when the server reaches it through another address.
The validator service exposes `/api/runs/{id}/webhooks` under `openfsp.validator.public-url` while the run is running.
During the final scenario the receiver answers `410 Gone`, after which the server may disable the endpoint. A webhook
scenario is inconclusive without a receiver and fails when the receiver got no delivery during the whole run.

Fee scenarios select their payment service by `payments.fee`: `payments/reported-fee` and `statements/entry-with-fee`
require it, `statements/entry-without-fees` excludes it.
