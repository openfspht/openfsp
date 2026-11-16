# Simulator

Plays MonCash, NatCash and a native OpenFSP provider (`mock_openfsp`), failures included, with a control surface for tests.

## Spaces

Each validation run gets its own space in the simulator, so several runs share one public simulator without seeing
each other's payments, scenarios, automatic mode, journal or statements. `POST /runs` opens a space and returns its
random 128-bit id and base URL. Every operator and control route then also answers under `/runs/{space}/`, for example
`/runs/{space}/mock_openfsp/v1/payments` or `/runs/{space}/control/reset`, and the hosted checkout links, discovery
URLs and MonCash redirects built in a space stay in it. An unknown or expired space answers 404. A space closes on
`DELETE /runs/{space}` or after `simulator.spaces.idle-lifetime` without a request (2 hours), and its state is purged;
`simulator.spaces.capacity` bounds the open spaces (256). The unscoped routes are the default space: `make dev`, the
console and the shop use it, and it never expires. The native operators keep their idempotency records in memory only,
for the life of the process or the space: the simulator is an ephemeral test target, not a durable store that honours
the retention of idempotency#6.1.

Besides the three simulated operators, the simulator serves two test-only profiles in the OpenFSP dialect
(conformance 9.3.5): `mock_no_source` (no lookup, callback or statement) and `mock_statement_only` (statement only).
Each offers only the scenarios its flags can produce. The gateway's `conformance` Spring profile declares both, with amount
bounds on `mock_no_source`, plus `mock_unreachable` on a closed port; `make gateway-conformance` runs it with `development,conformance`, as CI does, while
`make dev` keeps the real and mock operators only. `make validate` and CI pass the keys and the callback URL the assisted
scenarios ask for, attest through the admin API a `mock_openfsp` payment they create under `PENDING_FOREVER` and pass it
as `attested-payment`, and `make validate` says when the test-profile operators are missing.
