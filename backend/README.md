# Backend

Kotlin and Spring Boot, built with Gradle (`./gradlew build`, JDK 25).

| Module | Role |
|---|---|
| `protocol` | OpenFSP types and webhook signatures, shared by every module and published as `org.openfsp:openfsp-protocol` |
| `rules` | Payment request rules shared by the gateway and the simulator |
| `ddd` | The `@DomainService` and `@Stub` annotations |
| `gateway/domain`, `gateway/infrastructure` | The gateway: payment, webhook, statement, auth and capability rules; HTTP API, PostgreSQL, provider clients, admin |
| `simulator/domain`, `simulator/infrastructure` | Simulated providers and their scenarios; provider APIs, hosted checkout, control surface |
| `validator/domain`, `validator/infrastructure` | Conformance suite and engine, reports, attestations; validator API, the `validate` command |

Every `domain` module is plain Kotlin: the model and its domain services, `api/` for the use cases, `spi/` for what
they need, `spi/stubs/` for in-memory implementations. The matching `infrastructure` module implements the `spi` ports
and wires the domain services through a filtered component scan.
