# Hardening

How I expect the gateway to run outside development. It refuses to start when the first four points are not met.

1. **Profile.** Run without the `development` profile (`SPRING_PROFILES_ACTIVE=production`). The gateway then serves
   the `live` environment, accepts only `ofsp_live_` keys and never issues a key on its own.
2. **HTTPS.** Serve TLS with a Spring SSL bundle (`server.ssl.bundle`), or set
   `openfsp.tls-terminated-by-proxy=true` behind a proxy that terminates TLS and only forwards to the gateway on a
   private network. `openfsp.public-base-url` must be `https`.
3. **Order id derivation key.** At least 256 random bits, base64, in `openfsp.order-id-derivation-key`. Keep it: a new
   key only applies to new payments, and losing it breaks nothing already recorded.
4. **Webhook endpoints.** Every endpoint URL must be `https`.
5. **Secrets.** Provider credentials, the derivation key, the admin token and the webhook signing key come from the
   environment or from files in `OPENFSP_SECRETS_DIR` (default `/run/secrets/openfsp/`, one file per property, for
   example `openfsp.providers.moncash.api-key`). Never from a committed file.
6. **Webhook signing key.** Provide it with `openfsp.webhooks.signing-key.{id,private-key,public-key}` (PKCS#8 and
   X.509, base64), or let the gateway generate one. Rotate with `POST /admin/webhooks/keys/rotate`; add
   `?immediate=true` only after a compromise.
7. **Admin.** Set `openfsp.admin-token`, and do not expose `/admin` outside the operator network.
8. **Database.** A dedicated PostgreSQL role owning only the gateway schema, TLS to the database, encrypted backups.
9. **Audit.** `GET /admin/audit` lists authenticated requests, authentication failures, and key issuance and
   revocation. Ship the `openfsp.operator` log category to your alerting: it carries conflicts, abandoned webhooks and
   disabled endpoints.
