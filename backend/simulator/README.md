# Simulator

The simulator provides the reference mock server described in conformance 9. Its `mock_openfsp` service implements
the OpenFSP API for simulated payment services, including failure scenarios, and exposes a control interface for the
conformance suite. It also simulates the MonCash and NatCash APIs for gateway adapter testing.

## Spaces

Each validation run uses a dedicated simulator space. Multiple runs can therefore share a public simulator while
keeping payments, scenarios, automatic mode, request journals and statements isolated. `POST /runs` creates a space
and returns its random 128-bit identifier and base URL. All payment service and control routes are also available under
`/runs/{space}/`, for example `/runs/{space}/mock_openfsp/v1/payments` and `/runs/{space}/control/reset`. Hosted checkout
links, discovery URLs and MonCash redirects generated within a space remain scoped to it. Requests for an unknown or
expired space return 404. A space is closed and its state purged on `DELETE /runs/{space}` or after an inactivity period
configured by `openfsp.simulator.spaces.idle-lifetime` (2 hours). `openfsp.simulator.spaces.capacity` limits the number
of open spaces to 256. Unscoped routes use the default space, which is shared by `make dev`, the console and the shop
and does not close: its payments and idempotency records older than the idle lifetime are purged. Each space keeps its
last `openfsp.simulator.journal-capacity` requests (1,000). The simulated OpenFSP services retain idempotency records
only in memory. This ephemeral test environment does not provide the durable retention required by idempotency#6.1.
Control and space errors are problem documents (RFC 9457).

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

In addition to `mock_openfsp`, the simulator exposes the two test services defined in conformance 9.3.5 through the
OpenFSP API: `mock_no_source`, the base test service supporting payments only, and `mock_statement_only`, supporting
payments and statements. Each exposes only scenarios consistent with its capabilities. The gateway's `conformance`
Spring profile declares both services and sets amount limits for `mock_no_source`. `make gateway-conformance` starts
the gateway with `development,conformance`, matching CI, while `make
dev` retains only the real and mock payment services. `make validate` runs the suite against this gateway as a `server`.
It supplies the keys and inputs required by assisted scenarios, creates a `mock_openfsp` payment using
`PENDING_FOREVER`, records an attestation through the administration API and supplies the payment as `attested-payment`.
