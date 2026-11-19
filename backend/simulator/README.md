# Simulator

The simulator provides the reference mock server described in conformance 9. Its `mock_openfsp` service implements
the OpenFSP API for simulated payment services, including failure scenarios, and exposes the control interface of
conformance 4.9 for the conformance suite. It also simulates the MonCash and NatCash APIs for gateway adapter testing.

## Retention

The simulator keeps its state in memory, partitioned by merchant account: payments, scenarios, automatic mode and the
request journal. A sweep, run every `openfsp.simulator.retention-sweep-interval` (1 minute), purges payments and
idempotency records older than `openfsp.simulator.retention` (2 hours). The journal keeps the last
`openfsp.simulator.journal-capacity` requests (1,000). `POST /control/reset` clears the state the caller sees. The simulated
OpenFSP services retain idempotency records only in memory. This ephemeral test environment does not provide the durable
retention required by idempotency#6.1. Control errors are problem documents (RFC 9457).

Callbacks are sent with a 3 second connect timeout and a 7 second read timeout, without following redirects. A
callback URL that resolves to a loopback, private, link-local or metadata address is refused unless its host is listed
in `openfsp.simulator.http-callback-hosts` or `openfsp.simulator.callbacks.private-addresses-allowed` is set. The
default configuration, bound to 127.0.0.1, sets it for local development; the `docker` profile, bound to every
interface, does not.

Each simulated OpenFSP service prepares conformance runs at `POST /{provider}/conformance` and announces its control
interface as `control_endpoint`, `/{provider}/conformance/control` (conformance 4.9). Only the `api_key` of an active
preparation drives it, on the payments of that preparation: a published key or a restricted key is refused with 403,
resolving a payment that already has its outcome with 409, and an unreadable body with `malformed-request`: `PUT .../references/{reference}/scenario`, `GET .../references/{reference}`,
`POST .../payments/{id}/resolve` and `POST .../payments/{id}/callback`. A preparation lasts
`openfsp.simulator.conformance.run-lifetime` (1 hour), and the simulator holds at most
`openfsp.simulator.conformance.max-active-runs` active preparations (50), answering `429 rate-limited` beyond.

The simulator interface and the gateway tests use the routes under `/control` instead. A caller on the loopback
without a key acts on the whole instance. The nginx of the simulator interface forwards `/simulator/control/` only and
presents the key of `SIMULATOR_CONTROL_KEY` from its environment, the published `mock_openfsp` key in Compose; the
development server of the interface presents it too when set. Any other caller presents `Authorization: Bearer` with the primary key of a
merchant account, a published key or the `api_key` of a conformance run, and sees and changes only that account: its
payments, scenarios, journal and reset. A restricted key is refused with 403. The journal masks credential headers and every
body member or query parameter whose name holds `key`, `secret` or `token`.
`PUT /control/operators/{provider}/references/{reference}/scenario` assigns a scenario by reference and returns 404 for
a payment service whose order identifier differs from the reference. Gateway tests assign scenarios using the order
identifier derived by the gateway through `PUT /control/operators/{provider}/orders/{order}/scenario`.
`GET /control/operators/{provider}/references/{reference}` returns the payment id, whether the payment is settled and
the number of callbacks sent. The request journal is available at `/control/journal`.

The default configuration exposes `mock_openfsp`, `mock_moncash` and `mock_natcash` only. The `conformance` Spring
profile of the simulator adds the two test services defined in conformance 9.3.5 through the OpenFSP API:
`mock_no_source`, the base test service supporting payments only, and `mock_statement_only`, supporting payments and
statements. Each exposes only scenarios consistent with its capabilities. The gateway's `conformance` Spring profile
declares both services and sets amount limits for `mock_no_source`. `make simulator-conformance` and
`make gateway-conformance` start the simulator with `conformance` and the gateway with `development,conformance`,
matching CI, while `make dev` retains only the real and mock payment services. `make validate` runs the suite against
this gateway as a `server`, and `make validate TARGET=http://127.0.0.1:8090/mock_openfsp` against the simulated
operator; each prepares the run and supplies its keys and inputs.

The `TIMEOUT` scenario accepts the payment and answers `504 provider-timeout` at once (conformance 4.9.3), while
`TIMEOUT_THEN_SUCCESS` and `TIMEOUT_THEN_NOTHING` hold their answer for `openfsp.simulator.hang-duration` (40 seconds).

`GET /{provider}/v1/statements` lists the succeeded payments of a simulated OpenFSP service for a business day or a
range (lifecycle 10), with the payment id, its reference and its completion time. A service whose profile sets
`statements: false` answers `422 capability-not-supported`, as does a creation carrying `callback_url` at a service
without `callbacks` (capabilities 4.4). A search by reference reads the stored payment even without `lookup`.

A simulated OpenFSP service reports a fee when its profile sets `fee.rate-basis-points` and `fee.bearer` (`merchant`
or `payer`). It then announces `payments.fee`, and a succeeded payment carries `fee` in its resource, in its
`payment.succeeded` event and in its statement entry: the amount times the rate, rounded half up to the centime.
`mock_openfsp` is configured with 200 basis points borne by the merchant. Without the setting a service reports no fee
and does not announce the capability. The MonCash and NatCash dialects never report one, as their APIs carry none.
