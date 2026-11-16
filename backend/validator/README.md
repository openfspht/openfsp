# Validator

Runs the conformance suite against a gateway or a native provider: `java -jar app.jar validate ...` on the command line, or the web service behind the validator page.

## Web service

The validator web service runs `openfsp.validator.concurrency` runs at once (4) and answers 409 `busy` beyond. A run
takes two steps. `POST /api/runs` with `{"target":"gateway"}` opens a simulator space and returns the run id and the
operator URLs to configure on the gateway; `POST /api/runs/{id}/start` with the base URL and the keys starts it within
`openfsp.validator.prepared-run-lifetime` (1 hour). The engine connects only to the addresses checked at start.

Behind a reverse proxy, set `openfsp.validator.behind-trusted-proxy=true`: the validator service then runs with
`server.forward-headers-strategy=framework` and the per-address rate limit reads the client from `X-Forwarded-For`. The
proxy must overwrite that header with the client address, never append to it, and the validator service must not be reachable
around the proxy. The page's nginx does both in `compose.yaml`.

Running it for real: [HARDENING.md](../../HARDENING.md).

## Conformance attestations

`POST /api/runs/{id}/attestation` signs the results of a finished run, whatever the verdict: the attestation records
one run, failures included, and certifies no implementation (conformance 6.3, 7.6). A second call returns the same
attestation. It is a JWS in compact serialization (RFC 7515) with `alg` `EdDSA` (Ed25519), `kid`, `typ` `JOSE` and
`cty` `openfsp-attestation+json`. The payload is the attestation in RFC 8785 canonical JSON, of type
`openfsp-conformance-attestation/1`: id, issuer, issue date, claim, suite, target and its base URL, run, redacted
configuration, Core and profile verdicts, limits, validity, and `report.digest`, the SHA-256 of the RFC 8785 form of the
stored report.

- `GET /api/attestations/{id}`: the decoded attestation, the JWS and the links below.
- `GET /api/attestations/{id}/verify`: signature, key and digest checked against the stored report.
- `GET /api/attestations/{id}.pdf` (`?lang=fr` or `Accept-Language`), `GET /api/attestations/{id}/badge.svg`.
- `GET /.well-known/openfsp-validator`: `issuer` and `attestation_keys`, a JWK Set with `status` `active` or `retired`.
- `/attestations/{id}` on the validator page, also the target of the PDF QR code and of the badge link.

The signing key is an Ed25519 PKCS#8 key (`openssl genpkey -algorithm ed25519 -out attestation.pem`), given by
`openfsp.validator.attestation.signing-key.private-key-file` or `.private-key`, from the environment or from a file in
`OPENFSP_VALIDATOR_SECRETS_DIR` (default `/run/secrets/openfsp-validator/`). Its `kid` is `signing-key.id`, or its RFC
7638 thumbprint. In development mode, the validator service generates one at first start and keeps it in its database. To
rotate, configure the new key and restart: the earlier keys stay published as `retired`, so their attestations still
verify. Without a key outside development mode, issuing answers 503 `attestation-unavailable`.
`openfsp.validator.attestation.public-url` is the issuer and the base of the page links. It and `openfsp.validator.public-url` default to
`https://validate.openfsp.org`. `openfsp.validator.simulator.control-url` and `.public-url` have no default: the service
does not start without the address of its simulator.

Offline, with the attestation id in `$id` and the validator at `$V`:

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

The last two lines print the same digest when the report is the attested one; the Python form matches RFC 8785 for the
strings and integers a report holds.
