import type { EventType, Payment, PaymentStatus } from "@workspace/types"

export interface WebhookEvent {
  id: string
  type: EventType
  created_at: string
  sequence: number
  resource_type: "payment"
  resource_id: string
  reference: string
  previous_status?: PaymentStatus
  data: Payment
}

export type Verification = { verified: true; event: WebhookEvent } | { verified: false; reason: string }

const COMPONENTS = '("@method" "@target-uri" "content-type" "content-digest" "openfsp-event-id" "openfsp-event-type")'
const TAG = "openfsp-webhook"
const MAX_VALIDITY_SECONDS = 300

interface Jwk {
  kid: string
  kty: string
  crv: string
  x: string
  openfsp_status: "active" | "retired"
}

function parseSignatureInput(header: string) {
  const value = header.trim().replace(/^sig1=/, "")
  if (!value.startsWith(COMPONENTS)) return undefined
  const params = new Map<string, string>()
  for (const part of value.slice(COMPONENTS.length).split(";").filter(Boolean)) {
    const [key, raw] = part.split("=", 2)
    if (key === undefined || raw === undefined) return undefined
    params.set(key, raw.replace(/^"|"$/g, ""))
  }
  const created = Number(params.get("created"))
  const expires = Number(params.get("expires"))
  const keyId = params.get("keyid")
  const nonce = params.get("nonce")
  if (!Number.isInteger(created) || !Number.isInteger(expires) || !keyId || !nonce) return undefined
  return { created, expires, keyId, nonce, alg: params.get("alg"), tag: params.get("tag"), serialized: value }
}

function base64(bytes: ArrayBuffer): string {
  return Buffer.from(bytes).toString("base64")
}

function constantTimeEqual(left: string, right: string): boolean {
  const a = new TextEncoder().encode(left)
  const b = new TextEncoder().encode(right)
  if (a.length !== b.length) return false
  let difference = 0
  for (let i = 0; i < a.length; i++) difference |= a[i]! ^ b[i]!
  return difference === 0
}

export class WebhookVerifier {
  private keys = new Map<string, CryptoKey>()
  private nonces = new Map<string, number>()
  private lastRefresh = 0

  private readonly gatewayUrl: string
  private readonly now: () => number

  constructor(gatewayUrl: string, now: () => number = () => Math.floor(Date.now() / 1000)) {
    this.gatewayUrl = gatewayUrl
    this.now = now
  }

  async verify(request: { method: string; targetUri: string; headers: Headers; body: Uint8Array<ArrayBuffer> }): Promise<Verification> {
    const reject = (reason: string): Verification => ({ verified: false, reason })
    const params = parseSignatureInput(request.headers.get("signature-input") ?? "")
    if (!params) return reject("missing or malformed Signature-Input")
    if (params.tag !== TAG) return reject("unexpected tag")
    const key = await this.key(params.keyId)
    if (!key) return reject("unknown keyid")
    if (params.alg !== "ed25519") return reject("alg does not match the key")

    const now = this.now()
    if (params.expires - params.created > MAX_VALIDITY_SECONDS) return reject("validity window too long")
    if (now > params.expires) return reject("signature expired")
    if (params.created > now + MAX_VALIDITY_SECONDS) return reject("signature created in the future")
    this.forgetExpiredNonces(now)
    if (this.nonces.has(params.nonce)) return reject("nonce already used")

    const digest = request.headers.get("content-digest") ?? ""
    const expected = `sha-256=:${base64(await crypto.subtle.digest("SHA-256", request.body))}:`
    if (!constantTimeEqual(digest, expected)) return reject("Content-Digest does not match the body")

    const signature = (request.headers.get("signature") ?? "").trim().replace(/^sig1=:/, "").replace(/:$/, "")
    const base = [
      `"@method": ${request.method.toUpperCase()}`,
      `"@target-uri": ${request.targetUri}`,
      `"content-type": ${request.headers.get("content-type")}`,
      `"content-digest": ${digest}`,
      `"openfsp-event-id": ${request.headers.get("openfsp-event-id")}`,
      `"openfsp-event-type": ${request.headers.get("openfsp-event-type")}`,
      `"@signature-params": ${params.serialized}`,
    ].join("\n")
    const valid = await crypto.subtle.verify("Ed25519", key, Buffer.from(signature, "base64"), new TextEncoder().encode(base))
    if (!valid) return reject("invalid signature")

    this.nonces.set(params.nonce, params.expires)
    return { verified: true, event: JSON.parse(new TextDecoder().decode(request.body)) as WebhookEvent }
  }

  private async key(keyId: string): Promise<CryptoKey | undefined> {
    if (!this.keys.has(keyId) && Date.now() - this.lastRefresh > 10_000) await this.refresh()
    return this.keys.get(keyId)
  }

  private async refresh() {
    this.lastRefresh = Date.now()
    const response = await fetch(new URL("/v1/webhooks/keys", this.gatewayUrl))
    const { keys } = (await response.json()) as { keys: Jwk[] }
    for (const jwk of keys) {
      if (jwk.kty !== "OKP" || jwk.crv !== "Ed25519") continue
      this.keys.set(jwk.kid, await crypto.subtle.importKey("jwk", { kty: "OKP", crv: "Ed25519", x: jwk.x }, { name: "Ed25519" }, false, ["verify"]))
    }
  }

  private forgetExpiredNonces(now: number) {
    for (const [nonce, expires] of this.nonces) if (expires < now) this.nonces.delete(nonce)
  }
}
