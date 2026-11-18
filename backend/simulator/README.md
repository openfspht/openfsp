# Simulator

The simulator provides the reference mock server described in conformance 9. Its `mock_openfsp` service implements
the OpenFSP API for simulated payment services, including failure scenarios, and exposes a control interface for the
conformance suite. It also simulates the MonCash and NatCash APIs for gateway adapter testing.

## Retention

The simulator keeps a single state in memory, shared by every caller: payments, scenarios, automatic mode, the request
journal and statements. A sweep, run every `openfsp.simulator.retention-sweep-interval` (1 minute), purges payments and
idempotency records older than `openfsp.simulator.retention` (2 hours). The journal keeps the last
`openfsp.simulator.journal-capacity` requests (1,000). `POST /control/reset` clears the whole state. The simulated
OpenFSP services retain idempotency records only in memory. This ephemeral test environment does not provide the durable
retention required by idempotency#6.1. Control errors are problem documents (RFC 9457).

Callbacks are sent with a 5 second connect timeout and a 10 second read timeout, without following redirects. A
callback URL that resolves to a loopback, private, link-local or metadata address is refused unless its host is listed
in `openfsp.simulator.http-callback-hosts` or `openfsp.simulator.callbacks.private-addresses-allowed` is set. The
default configuration, bound to 127.0.0.1, sets it for local development; the `docker` profile, bound to every
interface, does not.

The suite assigns a scenario to a `mock_openfsp` payment by reference (conformance 9.4.2), using
`PUT /control/operators/{provider}/references/{reference}/scenario`. This route returns 404 for a payment service whose
order identifier differs from the reference. Gateway tests assign scenarios using the order identifier derived by the
gateway through `PUT /control/operators/{provider}/orders/{order}/scenario`.
`GET /control/operators/{provider}/references/{reference}` returns the payment id, whether the payment is settled and
the number of callbacks sent. The request journal is available at `/control/journal`.

The default configuration exposes `mock_openfsp`, `mock_moncash` and `mock_natcash` only. The `conformance` Spring
profile of the simulator adds the two test services defined in conformance 9.3.5 through the OpenFSP API:
`mock_no_source`, the base test service supporting payments only, and `mock_statement_only`, supporting payments and
statements. Each exposes only scenarios consistent with its capabilities. The gateway's `conformance` Spring profile
declares both services and sets amount limits for `mock_no_source`. `make simulator-conformance` and
`make gateway-conformance` start the simulator with `conformance` and the gateway with `development,conformance`,
matching CI, while `make dev` retains only the real and mock payment services. `make validate` runs the suite against
this gateway as a `server`. It supplies the keys and inputs required by assisted scenarios, creates a `mock_openfsp`
payment using `PENDING_FOREVER`, records an attestation through the administration API and supplies the payment as
`attested-payment`.
