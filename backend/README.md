# Backend

The backend uses Kotlin and Spring Boot. Build it with Gradle using `./gradlew build` and JDK 25.

| Module | Role |
|---|---|
| `shared/protocol` | OpenFSP types and webhook signatures, shared by every module and published as `org.openfsp:openfsp-protocol` |
| `shared/server` | Server-side helpers (problems, digests, request fingerprints, payment request rules), not published |
| `shared/http` | The guarded HTTP client used for outbound calls |
| `shared/ui` | Serves the web interface bundled in each image |
| `shared/ddd` | The `@DomainService` and `@Stub` annotations |
| `gateway/domain`, `gateway/infrastructure` | The gateway: payment, webhook, statement, auth and capability rules; HTTP API, PostgreSQL, provider clients, admin |
| `simulator/domain`, `simulator/infrastructure` | Simulated providers and their scenarios; provider APIs, hosted checkout, control surface |
| `validator/domain`, `validator/infrastructure` | Conformance suite and engine, reports, attestations; validator API, the `validate` command |

Each `domain` module contains the model and domain services in plain Kotlin. Use cases are defined in `api/`, their
required interfaces in `spi/`, and in-memory implementations in `spi/stubs/`. The corresponding `infrastructure`
module implements the `spi` ports and configures domain services through a filtered component scan.

## Conformance runs

With `openfsp.conformance.enabled`, refused outside the test environment, the gateway prepares conformance runs at
`POST /conformance` (conformance 4.8) and announces the control interface of the run as `control_endpoint`,
`/conformance/control` (conformance 4.9): `PUT .../references/{reference}/scenario`, `GET .../references/{reference}`,
`POST .../payments/{id}/resolve` and `POST .../payments/{id}/callback`. It is authenticated with the `api_key` of the
preparation, acts only on the payments created with the keys of that preparation and stops answering at `expires_at`.

A payment created with such a key under a reference that received a scenario is not sent to its provider: a scripted
payment service inside the gateway answers its creation and its lookups and sends its notifications, behind the same
provider port as the adapters, so the payment follows the usual creation, callback and synchronization paths. The
script keeps the identity and the capabilities of the provider named in the payment and uses only the channels that
provider offers: without notifications nothing is notified, without lookup nothing can be looked up, and with neither
the outcome of a scenario is reached at the payment service but never observed by the gateway. `resolve` gives its
outcome to the payment itself: for a provider with neither channel, the gateway records it as an attestation by the
control interface, with its event. A declaration is refused with 422 for
an unknown scenario, or for one that needs a channel no configured provider offers. A payment whose reference received
no scenario reaches its provider as usual. Scripts are held in memory and dropped when their run expires.
`openfsp.conformance.settle-after` (500 ms) and `openfsp.conformance.late-callback-after` (2 min) set the delays of the
scripted outcomes.

## Fees

The gateway records the fee of a payment (data model 8) only when the provider reports it with the terminal state: in
the creation answer, a lookup or a verified callback. It never computes one, and an absent fee never delays a
transition. A later report without a fee leaves the recorded one in place. The fee is published as `fee` on the payment
and on its statement entry, and as the `fee_amount` and `fee_bearer` columns of the statement CSV export, both empty
when the fee is unknown. `payments.fee` is declared for an OpenFSP provider whose operator announces it. The MonCash and
NatCash adapters report no fee, since neither API returns one, so the capability is never declared for them.
