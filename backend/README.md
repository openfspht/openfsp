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
