# Conformance

## Traceability

`traceability.yaml` lists every numbered clause of the English specification, in specification order, as `chapter#number`.
Each entry names who the clause binds (`server`, `client`, `operator`, `suite` or `informative`) and, for a server clause, its targets (`gateway`, `native-operator`).
It then says how the clause is verified: by requirements of `requirements.yaml` and their scenarios, by unit tests in this repository, by review when no automated test can observe it, or as a `gap` when a testable server obligation has no test yet.
`TraceabilityTest` in `backend/validator/domain` checks the enums, the cited requirements and test files, and that every requirement is cited; when the openfsp.org checkout sits next to this repository, it also checks that the file lists exactly the clauses of the specification.

## Running the suite

The `webhooks.emit` scenarios read the gateway's events from a receiver the engine runs for the duration of a run:
`--webhook-port` on the CLI (with `--webhook-bind` and `--webhook-url` when the gateway reaches it under another
address), `/api/runs/{id}/webhooks` on the validator service, under `openfsp.validator.public-url`. Registering it at the gateway
is configuration: `make validate` and CI add an endpoint for every payment event type through the admin API before the
run and remove it after. The last scenario answers `410 Gone`, after which the gateway may disable that endpoint. Without
a receiver, or when the receiver gets no delivery at all, these scenarios are inconclusive.

A requirement lists the targets it applies to under `targets`, with `levels` when its profile differs by target; a
scenario or a step narrows them with its own `targets`, and `without-capability` keeps a scenario for operators that do
not announce that capability. Against a native operator (conformance 5.7), `{{provider}}` is the single provider the
target announces, `{{order}}` is the reference itself and the gateway's journal steps are skipped. A scenario that
declares an outcome or reads the journal needs the simulator control surface: `--simulator-url` on the CLI, and on
the validator service the run's own space when the base URL is one of its operators, so prepare a native-operator run to get its
`mock_openfsp` URL. Without it the scenario is assisted, since a real native operator has to provoke the outcome itself.
`make validate-native` runs the suite against `mock_openfsp` (`VALIDATE_NATIVE_BASE_URL`) and the CI conformance job
runs it after the gateway. It passes the keys `mock_openfsp` publishes (conformance 9.3.7): the active one, a revoked
one, an expired one and a second principal's, so the authentication and principal scenarios run and Core passes.
the validator service passes the same keys, from `native-inputs` in its operator configuration, to a native-operator run that
targets its space's `mock_openfsp`; inputs sent with the run take precedence. A journal step that holds `during` a
period observes it to the end and the report carries the observed duration under `observations`.
