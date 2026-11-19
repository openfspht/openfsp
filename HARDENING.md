# Hardening

The following requirements apply to gateway deployments outside development. Outside development mode, the gateway refuses to start when requirement 2 or 3 is not met, when the admin token is not an Argon2id hash, or when it is a hash of the published demo token `demo-admin-token`.

1. **Profile.** Run without the `development` profile. With no profile, the gateway serves the `live` environment,
   accepts only `ofsp_live_` keys and does not issue keys automatically. Development mode requires the explicit
   `development` profile.
2. **HTTPS.** Configure TLS using a Spring SSL bundle (`server.ssl.bundle`), or set
   `openfsp.tls-terminated-by-proxy=true` when a proxy terminates TLS and forwards requests to the gateway exclusively
   over a private network. `openfsp.public-base-url` must use `https`. The gateway reads `X-Forwarded-For` and
   `X-Forwarded-Proto` only from the addresses listed in `openfsp.trusted-proxies` (none by default); without them,
   rate limits apply to the address of the proxy.
3. **Application key.** Set `OPENFSP_APP_KEY` (`openfsp.app-key`) to at least 256 random bits encoded in base64, for
   example `openssl rand -base64 32`. The gateway derives a distinct key from it, by HKDF-SHA256, for the order ids sent
   to payment services, the per-payment callback URLs, administrator sessions, the credentials of merchant accounts and
   the webhook signing keys stored in the database, which are sealed with AES-256-GCM bound to their `kid`.
   The repository holds no key: `make keys` writes one to `.env` for local runs. Keep the key secret and stable.
   Replacing it signs out administrators, makes the stored credentials of merchant accounts unreadable (set them again
   from the console), makes the stored webhook signing keys unreadable (rotate them with `?immediate=true`) and stops
   recognising the callback URLs of pending payments, which reconciliation then settles.
4. **Credentials per merchant account.** For each payment service, a merchant account uses either the platform
   credentials of the configuration (`platform`, the default) or its own MonCash or NatCash credentials (`own`). The
   choice is recorded and never inferred: an account using its own credentials without an active revision has its new
   payments refused (`invalid-field` on `provider`), never served with the platform credentials. Storing credentials
   switches the account to `own`. Switching it to `platform` from the console or with
   `PUT /admin/accounts/{account}/providers/{provider}/mode` and `{"mode":"platform"}` retires its active revision and
   is audited; switching back to `own` requires an active revision, that is new credentials. The gateway seals the
   credentials with AES-256-GCM, bound to their account, payment service and revision, and never returns them. Each
   change creates a revision: the new one is `active` and serves new payments, the previous one becomes `retired`. A
   payment records the revision that created it, or the platform credentials, and every later call to the payment
   service for it uses them, whatever the mode becomes, notifications included. A retired revision stays sealed while a pending payment uses
   it; a scheduled job (`openfsp.credential-purge-interval`, 15 minutes by default) then wipes its values. Removing the
   credentials retires the active revision. After a compromise, revoke the revision from the console or with
   `POST /admin/accounts/{account}/providers/{provider}/revisions/{revision}/revoke`: its values are wiped at once, no
   call is made any more for its payments, which stay pending and are signalled to the operator until an attestation,
   and the account is left without active credentials of its own when the revision was active.
5. **Webhook endpoints.** All webhook endpoint URLs must use `https`.
6. **Secrets.** Supply payment service credentials, the application key, administration token and webhook signing key
   through the environment or files in `OPENFSP_SECRETS_DIR` (default `/run/secrets/openfsp/`). Use one file per property,
   for example `openfsp.providers.moncash.api-key`. Never commit secrets to the repository.
7. **Webhook signing key.** Supply the key through `openfsp.webhooks.signing-key.{id,private-key,public-key}` using
   base64-encoded PKCS#8 and X.509 formats, or allow the gateway to generate it. Public keys are published under
   `webhook_keys` in `/.well-known/openfsp-configuration`. Automatic rotation follows
   `openfsp.webhooks.rotate-key-every` (90 days by default; `0` disables rotation). A new key is published one day before
   it begins signing. Initiate manual rotation from the console or with `POST /admin/webhooks/keys/rotate`. Use
   `?immediate=true` only following a compromise.
8. **Admin.** Set `OPENFSP_ADMIN_TOKEN` to the Argon2id hash produced by `hash`; plaintext tokens are rejected at startup. The console creates a 20-minute session (`openfsp.admin-session-lifetime`) using an `HttpOnly`, `Secure`, `SameSite=Strict` cookie, recorded on the server and revoked at sign-out. Sign-in attempts are rate limited and audited. Restrict `/admin` to the operator's network.
9. **Database.** Use a dedicated PostgreSQL role that owns only the gateway schema, enable TLS for database connections and encrypt backups.
10. **Audit.** `GET /admin/audit` exposes authenticated requests, authentication failures, key issuance and revocation, merchant account opening and changes to credentials per account, revocations and mode switches included.
   Forward the `openfsp.operator` log category to the alerting system to monitor conflicts, abandoned webhook deliveries
   and disabled endpoints.
11. **Business day.** Configure the business-day time zone with `OPENFSP_TIMEZONE` (`openfsp.time-zone`), using an IANA identifier. The default is `America/Port-au-Prince`. Statements, reconciliation, expiries at statement closure and daily counts use this time zone.
