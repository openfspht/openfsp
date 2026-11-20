# Validator

The validator executes the conformance suite against an OpenFSP server through the `validate` command or the web service
used by the validator interface. The image serves the web service and the interface on port 8095.

## Command

```sh
docker run --rm -it ghcr.io/openfspht/validator:<version> validate https://api.example.com
```

The suite is bundled in the image (`OPENFSP_VALIDATOR_CONFORMANCE`). `validate --help` lists the options. The report is
printed in the terminal; `--report FILE` also writes it as JSON, in a mounted directory such as `-v "$PWD:/work"`. Exit
codes: `0` Core passed, `1` Core failed, `2` Core incomplete, `3` the target refused the run, `64` usage error, `65`
invalid suite, `70` internal error.

Webhook scenarios need a receiver that the target can reach. In the image the receiver listens on every interface
(`OPENFSP_VALIDATOR_WEBHOOK_BIND`): publish its port and give the URL the target calls, for example
`-p 8099:8099 ... --webhook-port 8099 --webhook-url https://receiver.example:8099/webhooks`. Without `--webhook-port`,
those scenarios are inconclusive with the reason `no webhook receiver is configured for this run`, never passed.

Unlike the web service, the command reaches any address, loopback and private ones included. To test a server on the
local machine, target `http://host.docker.internal:<port>` (with `--add-host=host.docker.internal:host-gateway` on
Linux), or run the container with `--network host`.

Every `NOT_APPLICABLE` scenario and profile carries a `reason_code` and a `reason`, in the terminal, the JSON report and
the interface: `provider-not-announced`, `capability-not-announced`, `capability-excluded` for a scenario,
`no-applicable-scenario` for a profile, which lists the reasons of its scenarios.

## Web service

The web service permits the number of concurrent runs configured by `openfsp.validator.concurrency` (4) and returns
409 `busy` when that limit is reached. Execution has two stages. First, `POST /api/runs` with `{"target":"server"}`
returns the run identifier and the webhook receiver of the run. Then, `POST /api/runs/{id}/start` starts the run with the
supplied base URL, within `openfsp.validator.prepared-run-lifetime` (1 hour). Keys are optional: a server that announces
`conformance_endpoint` provides its own (conformance 4.8), and its answer must follow `fixtures.json`, `control_endpoint`
included, on the origin of the server. A key supplied with the run comes with `control_endpoint` on that origin.
Scenarios requiring a selected payment outcome call that control interface (conformance 4.9) and fail without it. The engine connects only to addresses validated when the
run starts, for the control interface as for the API. Each response is read up to `openfsp.validator.max-response-bytes` (4 MiB) and
abandoned after `openfsp.validator.request-timeout` (30 seconds) in total, and a run sends no request after
`openfsp.validator.max-run-duration` (30 minutes).

The validator holds at most `openfsp.validator.max-prepared-runs` prepared runs that have not started (64) and answers
409 `busy` beyond. The webhook receiver of a run accepts deliveries only while the run is running, up to
`openfsp.validator.webhook-capacity` deliveries (500) and `openfsp.validator.webhook-bytes` bytes of bodies (16 MiB). A
delivery beyond either budget is answered 503 and counted, and a webhook scenario that then misses a delivery is
inconclusive with the number of refusals.

No Spring profile is active by default, and `openfsp.validator.development-mode` is off unless set: the `development`
profile turns it on for local work and logs a warning at startup.

When deploying behind a trusted reverse proxy, set `openfsp.validator.behind-trusted-proxy=true`. The validator then
uses `server.forward-headers-strategy=native` with `server.tomcat.remoteip.internal-proxies` limited to the loopback and
private networks: the client address is the rightmost `X-Forwarded-For` entry that is not such a proxy, so a value the
client writes itself is ignored. Every proxy must append to the header, as Traefik does, and direct access to the
validator that bypasses the proxies must be prevented.

The service serves its interface on `/`, `/runs/{id}`, `/reports/{id}` and `/attestations/{id}` with a
`Content-Security-Policy` that forbids framing, `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`,
`Permissions-Policy` and, over HTTPS, `Strict-Transport-Security`. Hashed assets under `/assets` are cached for a year;
the pages are revalidated on every load. Any other path keeps its problem 404.

Deployment guidance: [HARDENING.md](../../HARDENING.md).

## Conformance attestations

`POST /api/runs/{id}/attestation` signs the results of a completed run, regardless of its verdict. The attestation
records a single execution, including failures; it does not certify an implementation (conformance 6.3, 7.6). Repeated
calls return the same attestation. The signature uses JWS Compact Serialization (RFC 7515), with `alg` set to `EdDSA`
(Ed25519), `kid`, `typ` set to `JOSE`, and `cty` set to `openfsp-attestation+json`. The payload is an RFC 8785 canonical
JSON attestation of type `openfsp-conformance-attestation/1`. It includes the identifier, issuer, issue date, claim,
suite, target and base URL, run, redacted configuration, Core and profile verdicts, limits and validity.
`report.digest` contains the SHA-256 digest of the stored report in RFC 8785 canonical form. An attestation is kept
when its report is purged after `openfsp.validator.retention` (90 days); its verification then reports that the report is
no longer stored.

- `GET /api/attestations/{id}`: the decoded attestation, the JWS and the links below.
- `GET /api/attestations/{id}/verify`: signature, key and digest checked against the stored report.
- `GET /api/attestations/{id}.pdf` (`?lang=fr` or `Accept-Language`), `GET /api/attestations/{id}/badge.svg`.
- `GET /.well-known/openfsp-validator`: `issuer` and `attestation_keys`, a JWK Set with `status` `active` or `retired`.
- `/attestations/{id}` on the validator page, also the target of the PDF QR code and of the badge link.

Provide an Ed25519 PKCS#8 signing key, generated with `openssl genpkey -algorithm ed25519 -out attestation.pem`, through
`openfsp.validator.attestation.signing-key.private-key-file` or `.private-key`. Configuration is read from the environment
or files in `OPENFSP_VALIDATOR_SECRETS_DIR` (default `/run/secrets/openfsp-validator/`). The key's `kid` is taken from
`signing-key.id` or its RFC 7638 thumbprint. In development mode, the service generates a key on first startup and stores
it in its database. To rotate the key, configure a new one and restart the service. Previous keys remain published as
`retired`, allowing existing attestations to be verified. Outside development mode, issuance without a configured key
returns 503 `attestation-unavailable`, no key is generated, and any private key stored in the database is erased at
startup. `openfsp.validator.attestation.revoked-kids` lists compromised keys: they leave the published key set, every
attestation they signed fails verification, and the service refuses to sign with a configured key on that list. `openfsp.validator.public-url` defines the issuer, the base URL of
interface links and the webhook address given to the server under test. It defaults to `https://validator.openfsp.org`.

To verify the signature locally and compare the report digest, set the attestation identifier in `$id` and the validator URL in `$V`. The following commands retrieve the published key and report from the validator:

```sh
b64url() { tr '_-' '/+' | awk '{ while (length($0) % 4) $0 = $0 "="; print }' | openssl base64 -d -A; }
jws=$(curl -fsS "$V/api/attestations/$id" | jq -r .signature.jws)
IFS=. read -r header payload signature <<< "$jws"
kid=$(printf '%s' "$header" | b64url | jq -r .kid)
x=$(curl -fsS "$V/.well-known/openfsp-validator" | jq -r --arg kid "$kid" '.attestation_keys[] | select(.kid == $kid) | .x')
{ printf '\x30\x2a\x30\x05\x06\x03\x2b\x65\x70\x03\x21\x00'; printf '%s' "$x" | b64url; } | openssl pkey -pubin -inform DER -out key.pem
printf '%s' "$signature" | b64url > signature.bin
printf '%s.%s' "$header" "$payload" > signing-input
openssl pkeyutl -verify -pubin -inkey key.pem -rawin -in signing-input -sigfile signature.bin
printf '%s' "$payload" | b64url > attestation.json
curl -fsS "$V/api/runs/$(jq -r .report.id attestation.json)/report" |
  python3 -c 'import json, sys; print(json.dumps(json.load(sys.stdin), sort_keys=True, separators=(",", ":"), ensure_ascii=False), end="")' |
  openssl dgst -sha256 -r
jq -r .report.digest attestation.json
```

The final two commands produce matching digests when the retrieved report is the attested report. The Python
serialisation shown matches RFC 8785 for the strings and integers used in these reports.
