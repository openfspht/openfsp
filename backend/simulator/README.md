# Simulator

The reference mock server of conformance 9: `mock_openfsp` implements the OpenFSP API for simulated providers, failures
included, with a control surface for the suite. It also plays the MonCash and NatCash dialects, so the gateway adapters
are tested against it.

## Spaces

Each validation run gets its own space in the simulator, so several runs share one public simulator without seeing
each other's payments, scenarios, automatic mode, request journal or statements. `POST /runs` opens a space and returns its
random 128-bit id and base URL. Every operator and control route then also answers under `/runs/{space}/`, for example
`/runs/{space}/mock_openfsp/v1/payments` or `/runs/{space}/control/reset`, and the hosted checkout links, discovery
URLs and MonCash redirects built in a space stay in it. An unknown or expired space answers 404. A space closes on
`DELETE /runs/{space}` or after `simulator.spaces.idle-lifetime` without a request (2 hours), and its state is purged;
`simulator.spaces.capacity` bounds the open spaces (256). The unscoped routes are the default space: `make dev`, the
console and the shop use it, and it never expires. The OpenFSP providers keep their idempotency records in memory only,
for the life of the process or the space: the simulator is an ephemeral test target, not a durable store that honours
the retention of idempotency#6.1.

The suite declares the scenario of a `mock_openfsp` payment under its reference (conformance 9.4.2), with
`PUT /control/operators/{provider}/references/{reference}/scenario`; the route answers 404 for a provider whose order
identifier is not the reference. The gateway tests declare scenarios under the order identifier the gateway derives,
with `PUT /control/operators/{provider}/orders/{order}/scenario`, and read the request journal at `/control/journal`.

Besides `mock_openfsp`, the simulator serves the two test providers of conformance 9.3.5 in the OpenFSP dialect:
`mock_no_source` (the base test provider: payments only) and `mock_statement_only` (payments and statement). Each offers
only the scenarios its capabilities can produce. The gateway's `conformance` Spring profile declares both, with amount
bounds on `mock_no_source`; `make gateway-conformance` runs it with `development,conformance`, as CI does, while `make
dev` keeps the real and mock providers only. `make validate` runs the suite against that gateway as a `server`: it passes
the keys and inputs the assisted scenarios ask for, attests through the admin API a `mock_openfsp` payment it creates
under `PENDING_FOREVER` and passes it as `attested-payment`.
