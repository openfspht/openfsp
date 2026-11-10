# OpenFSP gateway

The reference implementation I am building for the [OpenFSP specification](https://github.com/openfspht/adrs):
a gateway the merchant hosts, its admin console, and a simulator of payment operators with its own interface.

```mermaid
flowchart LR
    App["Merchant app"] -- "OpenFSP API" --> GW["gateway"]
    GW -- "operator API" --> Sim["simulator"]
    GW -- "signed webhook" --> App
    Console["console"] -- "admin session" --> GW
    SimUI["simulator UI"] --> Sim
```

| Module | Role | Stack |
|---|---|---|
| `protocol/` | OpenFSP types and webhook signatures, shared by every module | Kotlin |
| `ddd/` | The `@DomainService` and `@Stub` annotations | Kotlin |
| `gateway/domain/` | Payment, webhook, statement, auth and capability rules, with their `api` and `spi` ports | Kotlin, no framework |
| `gateway/infrastructure/` | HTTP API, PostgreSQL, operator clients, webhook delivery, admin | Spring Boot |
| `simulator/domain/` | Simulated operators and their scenarios | Kotlin, no framework |
| `simulator/infrastructure/` | Operator API, hosted checkout, control surface | Spring Boot |
| `frontend/apps/console` | Gateway dashboard: payments, statements, keys; signed in with the admin token | React, Vite, shadcn/ui |
| `frontend/apps/simulator` | Simulator dashboard: scenarios, simulated payments, request journal, statements | React, Vite, shadcn/ui |
| `frontend/apps/shop` | Demo merchant: checkout, verified webhooks | Bun, React |
| `frontend/packages/` | Shared TypeScript config, API types, client, atomic design system | TypeScript |

Each `domain` module follows a hexagonal layout: the model and its domain services, `api/` for the use
cases, `spi/` for what they need, `spi/stubs/` for in-memory implementations. The `infrastructure`
module implements the `spi` ports and wires the domain services through a filtered component scan.

## Run

Requires JDK 25, Docker and Bun.

```
./gradlew :simulator:infrastructure:bootRun   # http://localhost:8090
./gradlew :gateway:infrastructure:bootRun     # http://localhost:8080, starts PostgreSQL from compose.yaml
cd frontend && bun install && bun dev  # console http://localhost:5173, simulator UI http://localhost:5175
OPENFSP_API_KEY=<key printed by the gateway> bun --cwd frontend/apps/shop dev  # shop http://localhost:5174
```

Clients discover the endpoints at `/.well-known/openfsp-configuration`.

The console asks for the admin token. The gateway only holds its Argon2id hash, printed by the image:

```
docker run --rm -it <gateway image> hash   # prints OPENFSP_ADMIN_TOKEN='$argon2id$...'
```

Without `OPENFSP_ADMIN_TOKEN`, `/admin` answers the loopback only. The demo in `compose.yaml` uses the token `demo-admin-token`.

CI runs `./gradlew build`, then `bun run lint`, `bun run typecheck`, `bun run test` and `bun run build` in `frontend/`.

Running it for real: [docs/hardening.md](docs/hardening.md).

## License

Apache-2.0.
