<p align="center">
  <a href="https://openfsp.org">
    <picture>
      <img src="https://cdn.openfsp.org/logo/openfsp-logo.svg" alt="OpenFSP" width="280">
    </picture>
  </a>
</p>

<p align="center"><b>Reference implementation and integration tools for OpenFSP</b></p>

<p align="center">
  <a href="https://github.com/openfspht/openfsp/actions/workflows/ci.yml"><img src="https://github.com/openfspht/openfsp/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-0B3B36" alt="Apache-2.0"></a>
  <a href="https://openfsp.org/docs/spec"><img src="https://img.shields.io/badge/spec-openfsp.org-E8A33D" alt="Specification"></a>
</p>

[OpenFSP](https://openfsp.org) is an open specification for interoperability between merchant applications and payment
service providers in Haiti. This repository holds a gateway that exposes MonCash and NatCash through the OpenFSP API, a
simulator, the conformance validator and the client SDKs.

| Path | What |
|---|---|
| [`backend/gateway`](backend/gateway) | Merchant-hosted gateway: OpenFSP API, provider adapters, admin console |
| [`backend/simulator`](backend/simulator) | Reference server `mock_openfsp` and simulated MonCash and NatCash APIs |
| [`backend/validator`](backend/validator) | Conformance suite runner for `server` and `client` targets, as a command and a web service |
| [`frontend`](frontend) | Console, simulator and validator interfaces, served by their own image |
| [`sdks`](sdks) | Client SDKs for [Kotlin and Java](sdks/kotlin) and [TypeScript](sdks/typescript) |
| [`conformance`](conformance), [`openapi`](openapi) | Requirements, scenarios, JSON Schemas and the OpenAPI contract |

A merchant application built on the SDK lives in [openfspht/demo](https://github.com/openfspht/demo).

## Try it

```sh
make demo
```

This creates `.env` from `.env.example` with a fresh application key, then starts the stack with Docker Compose.

| Service | URL |
|---|---|
| Console (token `demo-admin-token`) | http://console.127.0.0.1.sslip.io |
| Gateway API | http://gateway.127.0.0.1.sslip.io |
| Simulator | http://simulator.127.0.0.1.sslip.io |
| Validator | http://validator.127.0.0.1.sslip.io |

## Images

`gateway`, `simulator` and `validator` are published on GHCR (`ghcr.io/openfspht/<image>`) and Docker Hub
(`openfsp/<image>`). Each runs one process serving its API and its interface, and takes a command instead:

```sh
docker run --rm -it ghcr.io/openfspht/gateway:<version> hash
docker run --rm ghcr.io/openfspht/validator:<version> validate https://api.example.com
```

`hash` prints the Argon2id hash of an admin token, to set as `OPENFSP_ADMIN_TOKEN`. It refuses the demo token.

`validate` runs the bundled conformance suite against a server and exits with `0` when Core passes, `1` when it fails,
`2` when it is incomplete, `3` when the target refuses the run, `64` on a usage error and `69` when the target is
unreachable. Useful options:

- `--report report.json` with `-v "$PWD:/work"` keeps the JSON report.
- `--webhook-port 8099 --webhook-url https://receiver.example:8099/webhooks` with `-p 8099:8099` runs the webhook
  scenarios, which are inconclusive without a reachable receiver.
- `http://host.docker.internal:8080` targets a server on the host (add `--add-host=host.docker.internal:host-gateway`
  on Linux).

Errors go to Sentry when `SENTRY_DSN` is set, and to `OPENFSP_UI_SENTRY_DSN` for the interfaces. Nothing is sent
otherwise.

Deployment: [openfsp.org/docs/gateway](https://openfsp.org/docs/gateway).

## Develop

Requires JDK 25, Bun, and PostgreSQL on `127.0.0.1:5432` with an `openfsp` database and role.

```sh
make install
make dev           # gateway :8080, simulator :8090, validator :8095, interfaces :5173, :5175, :5176
make dev-sandbox   # gateway on the MonCash and NatCash sandboxes, credentials from .env
make validate      # conformance suite against the local gateway
make check         # build, lint, format, typecheck and test everything
```

`make` lists every target. `lefthook install` runs the staged checks before a commit, `make check` before a push and
validates the commit subject.

The applications share one version (`vX.Y.Z`); each SDK has its own (`sdk-kotlin-vX.Y.Z`, `sdk-typescript-vX.Y.Z`).

## License

[Apache-2.0](LICENSE)
