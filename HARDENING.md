# Hardening

The following requirements apply to gateway deployments outside development. The gateway refuses to start if any of the first four requirements is not satisfied.

1. **Profile.** Run without the `development` profile (`SPRING_PROFILES_ACTIVE=production`). In this configuration, the
   gateway serves the `live` environment, accepts only `ofsp_live_` keys and does not issue keys automatically.
2. **HTTPS.** Configure TLS using a Spring SSL bundle (`server.ssl.bundle`), or set
   `openfsp.tls-terminated-by-proxy=true` when a proxy terminates TLS and forwards requests to the gateway exclusively
   over a private network. `openfsp.public-base-url` must use `https`.
3. **Order id derivation key.** Set `openfsp.order-id-derivation-key` to at least 256 random bits encoded in base64.
   Retain this key securely. A replacement key applies only to new payments; loss of the key does not invalidate
   existing payment records.
4. **Credentials per merchant account.** A merchant account may hold its own MonCash or NatCash credentials, which take
   precedence over the platform credentials of the configuration. Set `openfsp.credentials-key` (`OPENFSP_CREDENTIALS_KEY`)
   to exactly 256 random bits encoded in base64: the gateway seals these credentials with AES-256-GCM, bound to their
   account and payment service, and never returns them. Without this key, credentials per account cannot be stored. Losing
   it makes stored credentials unreadable; set them again from the console.
5. **Webhook endpoints.** All webhook endpoint URLs must use `https`.
6. **Secrets.** Supply payment service credentials, the derivation key, the credentials key, administration token and webhook signing key
   through the environment or files in `OPENFSP_SECRETS_DIR` (default `/run/secrets/openfsp/`). Use one file per property,
   for example `openfsp.providers.moncash.api-key`. Never commit secrets to the repository.
7. **Webhook signing key.** Supply the key through `openfsp.webhooks.signing-key.{id,private-key,public-key}` using
   base64-encoded PKCS#8 and X.509 formats, or allow the gateway to generate it. Public keys are published under
   `webhook_keys` in `/.well-known/openfsp-configuration`. Automatic rotation follows
   `openfsp.webhooks.rotate-key-every` (90 days by default; `0` disables rotation). A new key is published one day before
   it begins signing. Initiate manual rotation from the console or with `POST /admin/webhooks/keys/rotate`. Use
   `?immediate=true` only following a compromise.
8. **Admin.** Set `OPENFSP_ADMIN_TOKEN` to the Argon2id hash produced by `hash`; plaintext tokens are rejected at startup. The console creates a 20-minute session (`openfsp.admin-session-lifetime`) using an `HttpOnly`, `Secure`, `SameSite=Strict` cookie. Sign-in attempts are rate limited and audited. Restrict `/admin` to the operator's network.
9. **Database.** Use a dedicated PostgreSQL role that owns only the gateway schema, enable TLS for database connections and encrypt backups.
10. **Audit.** `GET /admin/audit` exposes authenticated requests, authentication failures, key issuance and revocation, merchant account opening and changes to credentials per account.
   Forward the `openfsp.operator` log category to the alerting system to monitor conflicts, abandoned webhook deliveries
   and disabled endpoints.
11. **Business day.** Configure the business-day time zone with `OPENFSP_TIMEZONE` (`openfsp.time-zone`), using an IANA identifier. The default is `America/Port-au-Prince`. Statements, reconciliation, expiries at statement closure and daily counts use this time zone.
