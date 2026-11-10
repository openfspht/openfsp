import { afterAll, beforeAll, beforeEach, expect, test } from "bun:test"

import { WebhookVerifier } from "./webhooks"

const url = "https://shop.example/webhooks/openfsp"
const now = 1_786_977_371
let keys: CryptoKeyPair
let other: CryptoKeyPair
let published: object[] = []
const realFetch = globalThis.fetch

async function publicJwk(pair: CryptoKeyPair, kid: string, status: string) {
  const { x } = await crypto.subtle.exportKey("jwk", pair.publicKey)
  return { kid, kty: "OKP", crv: "Ed25519", x, openfsp_status: status }
}

beforeAll(async () => {
  keys = (await crypto.subtle.generateKey({ name: "Ed25519" }, true, ["sign", "verify"])) as CryptoKeyPair
  other = (await crypto.subtle.generateKey({ name: "Ed25519" }, true, ["sign", "verify"])) as CryptoKeyPair
  globalThis.fetch = (async () => Response.json({ webhook_keys: published })) as unknown as typeof fetch
})

beforeEach(async () => {
  published = [await publicJwk(keys, "gw-test", "active")]
})

afterAll(() => {
  globalThis.fetch = realFetch
})

async function signed(body: string, nonce = "Zk9tQ1p2WXhLbFEyNw", created = now, keyId = "gw-test", pair = keys) {
  const bytes = new TextEncoder().encode(body)
  const digest = `sha-256=:${Buffer.from(await crypto.subtle.digest("SHA-256", bytes)).toString("base64")}:`
  const params = `("@method" "@target-uri" "content-type" "content-digest" "openfsp-event-id" "openfsp-event-type");created=${created};expires=${created + 300};keyid="${keyId}";nonce="${nonce}";alg="ed25519";tag="openfsp-webhook"`
  const base = [
    '"@method": POST',
    `"@target-uri": ${url}`,
    '"content-type": application/json',
    `"content-digest": ${digest}`,
    '"openfsp-event-id": evt_1',
    '"openfsp-event-type": payment.succeeded',
    `"@signature-params": ${params}`,
  ].join("\n")
  const signature = Buffer.from(await crypto.subtle.sign("Ed25519", pair.privateKey, new TextEncoder().encode(base))).toString("base64")
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

test("a key removed from the configuration is no longer accepted", async () => {
  let clock = now
  const verifier = new WebhookVerifier("https://gateway.example", () => clock)
  expect((await verifier.verify(await signed(body, "cmVtb3ZlZC1rZXktZmlyc3Q"))).verified).toBe(true)
  published = [await publicJwk(other, "gw-next", "active")]
  clock = now + 11
  expect((await verifier.verify(await signed(body, "cmVtb3ZlZC1rZXktbmV4dA", now, "gw-next", other))).verified).toBe(true)
  expect(await verifier.verify(await signed(body, "cmVtb3ZlZC1rZXktYWdhaW4"))).toEqual({ verified: false, reason: "unknown keyid" })
})

test("a retired key still verifies", async () => {
  published = [await publicJwk(keys, "gw-test", "retired")]
  const result = await new WebhookVerifier("https://gateway.example", () => now).verify(await signed(body, "cmV0aXJlZC1rZXktdGVzdA"))
  expect(result.verified).toBe(true)
})

test("a key that is neither active nor retired is not accepted", async () => {
  published = [await publicJwk(keys, "gw-test", "revoked")]
  const result = await new WebhookVerifier("https://gateway.example", () => now).verify(await signed(body, "cmV2b2tlZC1rZXktdGVzdA"))
  expect(result).toEqual({ verified: false, reason: "unknown keyid" })
})
