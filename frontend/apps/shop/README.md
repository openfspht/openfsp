# Demo shop

A merchant built on the TypeScript SDK: a catalogue, a checkout that creates
OpenFSP payments and a receiver for signed webhooks. Orders live in memory and
disappear on restart.

```bash
OPENFSP_API_KEY=... bun run dev
```

| Variable | Default |
|---|---|
| `OPENFSP_API_KEY` | required |
| `OPENFSP_GATEWAY_URL` | `http://127.0.0.1:8080` |
| `OPENFSP_ALLOW_PLAIN_HTTP` | `false` |
| `OPENFSP_PROVIDERS` | every provider the gateway offers |
| `SHOP_WEBHOOK_URL` | `http://127.0.0.1:3000/webhooks/openfsp` |
| `SHOP_PUBLIC_URL` | `http://localhost:5174` |
| `HOST` | `127.0.0.1` |
| `PORT` | `3000` |

## Who sees an order

The shop has no accounts. The first order a browser places sets an
`HttpOnly` `shop_session` cookie, and `GET /api/orders` lists only the orders
placed under that cookie. `GET /api/orders/:reference` answers anyone who holds
the reference, which is random and plays the role of a bearer link, so the
payer can come back from the provider in another browser.

The server listens on loopback by default. The Docker image sets
`HOST=0.0.0.0` so the compose stack can reach it: keep that port off the
public internet.
