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
| [`frontend`](frontend) | Gateway console, simulator UI and validator page |
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

The demonstration shop is available at http://localhost:3000, the console at http://localhost:5173 (token `demo-admin-token`),
the simulator at http://localhost:5175 and the validator at http://localhost:5176.

Images are published on GHCR (`ghcr.io/openfspht/<image>`) and Docker Hub (`openfsp/<image>`): `gateway`, `simulator`,
`validator`, `console`, `simulator-ui`, `validator-ui`.

Each service reports errors to Sentry when `SENTRY_DSN` is set and sends nothing otherwise.

## Develop

Development requires JDK 25, Bun and PostgreSQL at `127.0.0.1:5432`, with an `openfsp` database and role.

```sh
make install
make dev      # gateway :8080, simulator :8090, console :5173, simulator UI :5175
make check    # build, lint, typecheck and test everything
```

Gateway deployment documentation: [openfsp.org/docs/gateway](https://openfsp.org/docs/gateway), [PROVIDERS.md](PROVIDERS.md),
[HARDENING.md](HARDENING.md).

## License

[Apache-2.0](LICENSE)
