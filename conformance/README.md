# Conformance

## Traceability

`traceability.yaml` lists every numbered clause of the English specification, in specification order, as `chapter#number`.
Each entry names who the clause binds (`server`, `client`, `operator`, `suite` or `informative`) and, for a server clause, its target (`server`).
It then says how the clause is verified: by requirements of `requirements.yaml` and their scenarios, by unit tests in this repository, by review when no automated test can observe it, or as a `gap` when a testable server obligation has no test yet.
`TraceabilityTest` in `backend/validator/domain` checks the enums, the cited requirements and test files, and that every requirement is cited; when the openfsp.org checkout sits next to this repository, it also checks that the file lists exactly the clauses of the specification.

## Running the suite

The suite has two targets, `server` and `client` (conformance 2). A requirement lists its targets under `targets`, with
`levels` when its profile differs by target, and a scenario or a step narrows them with its own `targets`.

`{{provider}}` is a provider the server announces in `/v1/capabilities`. The engine takes the first one that announces
the requirement's profile and the scenario's `with-capabilities`, and none of its `without-capabilities`; `--provider`
on the CLI picks one first when it qualifies. A scenario no announced provider qualifies for is not applicable.

A scenario that chooses a payment outcome declares it on the control surface of the mock server, under the payment's
`reference` (conformance 9.4.2): `--simulator-url` on the CLI, and on the validator service the run's own space when the
base URL is one of its providers. Against any other server the scenario is assisted, and the operator provokes the
outcome by its own test means (conformance 4.5, 4.7).

`make validate-native` runs the suite against `mock_openfsp` (`VALIDATE_NATIVE_BASE_URL`). It passes the keys
`mock_openfsp` publishes (conformance 9.3.7): the active one, a revoked one, an expired one and a second principal's, so
the authentication and principal scenarios run without operator action. The validator service passes the same keys,
from `inputs` in its provider configuration, to a run that targets its space's `mock_openfsp`; inputs sent with the run
take precedence.

The `webhooks.emit` scenarios read the server's events from a receiver the engine runs for the duration of a run:
`--webhook-port` on the CLI (with `--webhook-bind` and `--webhook-url` when the server reaches it under another
address), `/api/runs/{id}/webhooks` on the validator service, under `openfsp.validator.public-url`. Registering it at the
server is configuration. The last scenario answers `410 Gone`, after which the server may disable that endpoint. Without
a receiver, or when the receiver gets no delivery at all, these scenarios are inconclusive.
