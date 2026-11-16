<p align="center">
  <a href="https://openfsp.org">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://cdn.openfsp.org/logo/openfsp-logo-inverse.svg">
      <img src="https://cdn.openfsp.org/logo/openfsp-logo.svg" alt="OpenFSP" width="280">
    </picture>
  </a>
</p>

<p align="center"><b>The OpenFSP gateway, simulator, conformance suite and SDKs</b></p>

<p align="center">
  <a href="https://github.com/openfspht/openfsp/actions/workflows/ci.yml"><img src="https://github.com/openfspht/openfsp/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-0B3B36" alt="Apache-2.0"></a>
  <a href="https://openfsp.org/docs/spec"><img src="https://img.shields.io/badge/spec-openfsp.org-E8A33D" alt="Specification"></a>
</p>

[OpenFSP](https://openfsp.org) is an open standard for payment integration in Haiti. This repository implements it: a
gateway that makes MonCash and NatCash speak OpenFSP today, the tools to test against it, and the SDKs merchants use.

| Path | What |
|---|---|
| [`backend/gateway`](backend/gateway) | The gateway the merchant hosts: OpenFSP in, provider APIs out |
| [`backend/simulator`](backend/simulator) | The reference mock server `mock_openfsp`, plus the MonCash and NatCash dialects the gateway is tested against |
| [`backend/validator`](backend/validator) | The conformance suite runner for the `server` and `client` targets: command line and web service |
| [`frontend`](frontend) | Gateway console, simulator UI and validator page |
| [`sdks`](sdks) | [Kotlin and Java](sdks/kotlin) on Maven Central, [TypeScript](sdks/typescript) on npm |
| [`conformance`](conformance), [`openapi`](openapi) | Requirements, scenarios, JSON Schemas and the OpenAPI contract |

The demo shop, a merchant built on the TypeScript SDK, lives in [openfspht/demo-shop](https://github.com/openfspht/demo-shop).

## Try it

```sh
docker compose --profile demo up
```

The shop is on http://localhost:3000, the console on http://localhost:5173 (token `demo-admin-token`), the simulator
on http://localhost:5175 and the validator on http://localhost:5176.

Images are published on GHCR (`ghcr.io/openfspht/<image>`) and Docker Hub (`openfsp/<image>`): `gateway`, `simulator`,
`validator`, `console`, `simulator-ui`, `validator-ui`.

## Develop

JDK 25, Bun and PostgreSQL on `127.0.0.1:5432` (`openfsp` database and role).

```sh
make install
make dev      # gateway :8080, simulator :8090, console :5173, simulator UI :5175
make check    # build, lint, typecheck and test everything
```

Deploying the gateway: [openfsp.org/docs/gateway](https://openfsp.org/docs/gateway), [PROVIDERS.md](PROVIDERS.md),
[HARDENING.md](HARDENING.md).

## License

[Apache-2.0](LICENSE)
