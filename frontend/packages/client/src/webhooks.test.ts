import { afterAll, beforeAll, expect, test } from "bun:test"

import { WebhookVerifier } from "./webhooks"

const url = "https://shop.example/webhooks/openfsp"
const now = 1_786_977_371
let keys: CryptoKeyPair
let jwk: JsonWebKey
const realFetch = globalThis.fetch

beforeAll(async () => {
  keys = (await crypto.subtle.generateKey({ name: "Ed25519" }, true, ["sign", "verify"])) as CryptoKeyPair
  jwk = await crypto.subtle.exportKey("jwk", keys.publicKey)
  globalThis.fetch = (async () =>
    Response.json({ webhook_keys: [{ kid: "gw-test", kty: "OKP", crv: "Ed25519", x: jwk.x, openfsp_status: "active" }] })) as unknown as typeof fetch
})

afterAll(() => {
  globalThis.fetch = realFetch
})

async function signed(body: string, nonce = "Zk9tQ1p2WXhLbFEyNw", created = now) {
  const bytes = new TextEncoder().encode(body)
  const digest = `sha-256=:${Buffer.from(await crypto.subtle.digest("SHA-256", bytes)).toString("base64")}:`
  const params = `("@method" "@target-uri" "content-type" "content-digest" "openfsp-event-id" "openfsp-event-type");created=${created};expires=${created + 300};keyid="gw-test";nonce="${nonce}";alg="ed25519";tag="openfsp-webhook"`
  const base = [
    '"@method": POST',
    `"@target-uri": ${url}`,
    '"content-type": application/json',
    `"content-digest": ${digest}`,
    '"openfsp-event-id": evt_1',
    '"openfsp-event-type": payment.succeeded',
    `"@signature-params": ${params}`,
  ].join("\n")
  const signature = Buffer.from(await crypto.subtle.sign("Ed25519", keys.privateKey, new TextEncoder().encode(base))).toString("base64")
  const headers = new Headers({
    "content-type": "application/json",
    "content-digest": digest,
    "openfsp-event-id": "evt_1",
    "openfsp-event-type": "payment.succeeded",
    "signature-input": `sig1=${params}`,
    signature: `sig1=:${signature}:`,
  })
  return { method: "POST", targetUri: url, headers, body: bytes }
}

const body = JSON.stringify({ id: "evt_1", type: "payment.succeeded", sequence: 2 })

test("a signed delivery verifies and yields the event", async () => {
  const result = await new WebhookVerifier("https://gateway.example", () => now).verify(await signed(body))
  expect(result.verified).toBe(true)
  if (result.verified) expect(result.event.sequence).toBe(2)
})

test("a replayed nonce is rejected", async () => {
  const verifier = new WebhookVerifier("https://gateway.example", () => now)
  const request = await signed(body, "bm9uY2UtcmVwbGF5LXRlc3Q")
  await verifier.verify(request)
  expect(await verifier.verify(request)).toEqual({ verified: false, reason: "nonce already used" })
})

test("a modified body is rejected", async () => {
  const request = await signed(body, "bW9kaWZpZWQtYm9keS10ZXN0")
  const result = await new WebhookVerifier("https://gateway.example", () => now).verify({ ...request, body: new TextEncoder().encode("{}") })
  expect(result).toEqual({ verified: false, reason: "Content-Digest does not match the body" })
})

test("an expired signature is rejected", async () => {
  const result = await new WebhookVerifier("https://gateway.example", () => now + 301).verify(await signed(body, "ZXhwaXJlZC1zaWduYXR1cmU"))
  expect(result).toEqual({ verified: false, reason: "signature expired" })
})
