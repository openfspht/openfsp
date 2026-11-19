<p align="center">
  <a href="https://openfsp.org">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://cdn.openfsp.org/logo/openfsp-logo-inverse.svg">
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
service providers in Haiti. This repository contains a reference implementation, a simulator, conformance tools and
client SDKs. Its gateway supports adoption by exposing an OpenFSP interface to services with different APIs, including
MonCash and NatCash.

| Path | What |
|---|---|
| [`backend/gateway`](backend/gateway) | Merchant-hosted gateway exposing the OpenFSP API and integrating payment service APIs |
| [`backend/simulator`](backend/simulator) | Reference mock server `mock_openfsp` and simulated MonCash and NatCash APIs for adapter testing |
| [`backend/validator`](backend/validator) | Conformance suite runner for the `server` and `client` targets, with command-line and web service interfaces |
| [`frontend`](frontend) | Gateway console, simulator UI and validator page, served by the gateway, simulator and validator images |
| [`sdks`](sdks) | Client SDKs distributed through [Maven Central](sdks/kotlin) and [npm](sdks/typescript) |
| [`conformance`](conformance), [`openapi`](openapi) | Requirements, scenarios, JSON Schemas and the OpenAPI contract |

A demonstration merchant application using an OpenFSP client SDK is available in [openfspht/demo](https://github.com/openfspht/demo).

## Try it

```sh
make demo
```

`make demo` writes a local application key to `.env` (mode 0600) on first run, then starts
`docker compose --profile demo up --build`. To call Compose directly, run `make keys` first. Every port is bound to
127.0.0.1.

The demonstration shop is available at http://localhost:3000, the console at http://console.localhost:8080 (token
`demo-admin-token`), the simulator at http://localhost:8090 and the validator at http://localhost:8095. The gateway
serves the console and `/admin` only on its admin host, `console.localhost` in Compose.

Images are published on GHCR (`ghcr.io/openfspht/<image>`) and Docker Hub (`openfsp/<image>`): `gateway`, `simulator`
and `validator`. Each image runs one process that serves its API and its web interface.

Each service reports errors to Sentry when `SENTRY_DSN` is set and sends nothing otherwise. The web interfaces report to
the DSN of `OPENFSP_UI_SENTRY_DSN`, with `OPENFSP_UI_SENTRY_ENVIRONMENT`, `OPENFSP_UI_SENTRY_RELEASE` and
`OPENFSP_UI_SENTRY_TRACES_SAMPLE_RATE`.

## Commands

Each image starts its server by default and takes a command:

```sh
docker run --rm -it ghcr.io/openfspht/gateway:<version> hash
docker compose exec gateway gateway hash
docker run --rm -it ghcr.io/openfspht/validator:<version> validate https://api.example.com
docker run --rm ghcr.io/openfspht/validator:<version> validate --help
```

`hash` reads the admin token without echo (or from standard input), refuses the published demo token and prints the
Argon2id hash to set as `OPENFSP_ADMIN_TOKEN`. It starts no server and needs no configuration.

`validate` runs the conformance suite bundled in the image against a server, prints the report and exits with `0` when
Core passes, `1` when it fails, `2` when it is incomplete, `3` when the target refuses the run, `64` on a usage error.
Mount a directory to keep the JSON report: `-v "$PWD:/work" ... validate https://api.example.com --report report.json`.
Webhook scenarios need a receiver the target can reach: publish its port and give its public URL,
`-p 8099:8099 ... validate https://api.example.com --webhook-port 8099 --webhook-url https://receiver.example:8099/webhooks`.
Without a receiver they are inconclusive. The command reaches any address, private ones included: on the local machine,
test `http://host.docker.internal:8080` (add `--add-host=host.docker.internal:host-gateway` on Linux).

## Develop

Development requires JDK 25, Bun and PostgreSQL at `127.0.0.1:5432`, with an `openfsp` database and role.

```sh
make install
make dev      # gateway :8080, simulator :8090, validator :8095, console :5173, simulator UI :5175, validator UI :5176
make check    # backend, frontend, TypeScript SDK and OpenAPI: build, lint, format, typecheck and test
```

`lefthook install` sets up the Git hooks: staged file checks before a commit, `make check` before a push and a single
line Conventional Commit subject.

Gateway deployment documentation: [openfsp.org/docs/gateway](https://openfsp.org/docs/gateway), [PROVIDERS.md](PROVIDERS.md),
[HARDENING.md](HARDENING.md).

## License

[Apache-2.0](LICENSE)
