# Backend

The backend uses Kotlin and Spring Boot. Build it with Gradle using `./gradlew build` and JDK 25.

| Module | Role |
|---|---|
| `protocol` | OpenFSP types and webhook signatures, shared by every module and published as `org.openfsp:openfsp-protocol` |
| `rules` | Payment request rules shared by the gateway and the simulator |
| `ddd` | The `@DomainService` and `@Stub` annotations |
| `gateway/domain`, `gateway/infrastructure` | The gateway: payment, webhook, statement, auth and capability rules; HTTP API, PostgreSQL, provider clients, admin |
| `simulator/domain`, `simulator/infrastructure` | Simulated providers and their scenarios; provider APIs, hosted checkout, control surface |
| `validator/domain`, `validator/infrastructure` | Conformance suite and engine, reports, attestations; validator API, the `validate` command |

Each `domain` module contains the model and domain services in plain Kotlin. Use cases are defined in `api/`, their
required interfaces in `spi/`, and in-memory implementations in `spi/stubs/`. The corresponding `infrastructure`
module implements the `spi` ports and configures domain services through a filtered component scan.

## Fees

The gateway records the fee of a payment (data model 8) only when the provider reports it with the terminal state: in
the creation answer, a lookup or a verified callback. It never computes one, and an absent fee never delays a
transition. A later report without a fee leaves the recorded one in place. The fee is published as `fee` on the payment
and on its statement entry, and as the `fee_amount` and `fee_bearer` columns of the statement CSV export, both empty
when the fee is unknown. `payments.fee` is declared for an OpenFSP provider whose operator announces it. The MonCash and
NatCash adapters report no fee, since neither API returns one, so the capability is never declared for them.
