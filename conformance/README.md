# Conformance

## Traceability

`traceability.yaml` indexes every numbered clause of the English specification in specification order, using the
`chapter#number` format. Each entry identifies the responsible role (`server`, `client`, `operator`, `suite` or
`informative`) and, for server clauses, the test target (`server`). Verification is mapped to requirements in
`requirements.yaml` and their scenarios, repository unit tests, or review where automated observation is not possible.
A testable server obligation without a test is recorded as a `gap`. `TraceabilityTest` in `backend/validator/domain`
validates enumeration values, referenced requirements and test files, and checks that every requirement is referenced.
If an openfsp.org checkout is present alongside this repository, it also checks that the indexed clauses match the
specification exactly.

## Running the suite

The suite defines two targets, `server` and `client` (conformance 2). Each requirement declares its applicable targets
under `targets` and uses `levels` when its profile depends on the target. Scenarios and steps may restrict applicability
through their own `targets`.

`{{provider}}` identifies a payment service advertised by the server in `/v1/capabilities`. The engine selects the first
service that advertises the requirement's profile and all capabilities in the scenario's `with-capabilities`, while
advertising none of its `without-capabilities`. The CLI option `--provider` gives priority to a specified service if it
meets these conditions. A scenario is not applicable if no advertised service qualifies.

A scenario requiring a specific payment outcome chooses it in a `control` step, through the control interface the
server announces as `control_endpoint` when it prepares the run (conformance 4.9): `declare` sets the scenario of a
`reference`, `settled` waits for its payment, `resolve` gives a pending payment its outcome and `callback` triggers its
held event. The command line and the validator service both call this interface with the key of the run, over the
connection to the server under test. The report then carries `outcomes: suite`. When the server announces no control
interface, or refuses a scenario with 422, the scenario is assisted and the operator produces the required outcome
using its own test facilities (conformance 4.5, 4.7).

`make validate` executes the suite against the implementation at `TARGET`, the gateway at `http://127.0.0.1:8080` by
default; `make validate TARGET=http://127.0.0.1:8090/mock_openfsp` runs it against the simulated OpenFSP operator.
`REPORT` and `WEBHOOK_PORT` set the report file and the port of the webhook receiver. No key is passed: the command line
asks the target to prepare the run (conformance 4.8) and receives an active key, a revoked key, an expired key and a key
belonging to a second principal. Authentication and principal scenarios therefore execute without operator
intervention. Inputs given with `--input` take precedence, and a key given with `--api-key` replaces the preparation by
the server, control interface included. The command exits 0 when Core is incomplete through assisted scenarios only.

A server may prepare its own runs (conformance 4.8). When its configuration document announces `conformance_endpoint`,
the validator service needs only the server URL: it posts its webhook receiver to that endpoint and receives a test key
and the inputs the server can produce. The gateway serves the endpoint at `/conformance` when
`openfsp.conformance.enabled` is set, which it refuses outside the test environment; `openfsp.conformance.access-code`
restricts it to callers that present the code. Its answer carries the control interface of the run, described in
`backend/README.md`. A key or an input supplied with the run takes precedence.

The `webhooks.emit` scenarios inspect server events received by a webhook receiver active for the duration of the run.
Configure the CLI receiver with `--webhook-port`, plus `--webhook-bind` and `--webhook-url` when the server accesses it
through a different address. The validator service exposes `/api/runs/{id}/webhooks` under
`openfsp.validator.public-url`. Register this receiver as an endpoint in the target server's configuration. During the
final scenario, the receiver returns `410 Gone`, after which the server may disable the endpoint. These scenarios are
inconclusive if no receiver is configured or if the receiver receives no deliveries.

Fee scenarios select their payment service by `payments.fee`: `payments/reported-fee` and `statements/entry-with-fee`
require it, `statements/entry-without-fees` excludes it. Each is not applicable when no advertised service qualifies.
