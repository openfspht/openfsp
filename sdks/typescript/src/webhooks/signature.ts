export const COMPONENTS = '("@method" "@target-uri" "content-type" "content-digest" "openfsp-event-id" "openfsp-event-type")'
export const LABEL = "sig1"
export const TAG = "openfsp-webhook"
export const ALGORITHM = "ed25519"
export const MAX_VALIDITY_SECONDS = 300

export interface SignatureParams {
  readonly created: number
  readonly expires: number
  readonly keyId: string
  readonly nonce: string
  readonly alg: string
  readonly tag: string
}

export interface SignedMessage {
  readonly method: string
  readonly targetUri: string
  readonly contentType: string
  readonly contentDigest: string
  readonly eventId: string
  readonly eventType: string
}

export function serializeParams(params: SignatureParams): string {
  return `${COMPONENTS};created=${params.created};expires=${params.expires};keyid="${params.keyId}";nonce="${params.nonce}";alg="${params.alg}";tag="${params.tag}"`
}

export function parseParams(header: string): SignatureParams | undefined {
  const trimmed = header.trim()
  const value = trimmed.startsWith(`${LABEL}=`) ? trimmed.slice(LABEL.length + 1) : trimmed
  if (!value.startsWith(COMPONENTS)) return undefined
  const fields = new Map<string, string>()
  for (const part of value.slice(COMPONENTS.length).split(";")) {
    if (part.trim() === "") continue
    const separator = part.indexOf("=")
    if (separator < 0) return undefined
    fields.set(part.slice(0, separator), part.slice(separator + 1).replace(/^"(.*)"$/, "$1"))
  }
  const created = integer(fields.get("created"))
  const expires = integer(fields.get("expires"))
  const keyId = fields.get("keyid")
  const nonce = fields.get("nonce")
  const alg = fields.get("alg")
  const tag = fields.get("tag")
  if (created === undefined || expires === undefined || !keyId || !nonce || !alg || !tag) return undefined
  return { created, expires, keyId, nonce, alg, tag }
}

export function signatureBase(message: SignedMessage, params: SignatureParams): Uint8Array<ArrayBuffer> {
  return new TextEncoder().encode(
    [
      `"@method": ${message.method.toUpperCase()}`,
      `"@target-uri": ${message.targetUri}`,
      `"content-type": ${message.contentType}`,
      `"content-digest": ${message.contentDigest}`,
      `"openfsp-event-id": ${message.eventId}`,
      `"openfsp-event-type": ${message.eventType}`,
      `"@signature-params": ${serializeParams(params)}`,
    ].join("\n"),
  )
}

export async function contentDigest(body: Uint8Array<ArrayBuffer>): Promise<string> {
  return `sha-256=:${encodeBase64(new Uint8Array(await crypto.subtle.digest("SHA-256", body)))}:`
}

export function formatSignature(signature: Uint8Array): string {
  return `${LABEL}=:${encodeBase64(signature)}:`
}

export function parseSignature(header: string): Uint8Array<ArrayBuffer> | undefined {
  const value = header.trim().replace(new RegExp(`^${LABEL}=:`), "").replace(/:$/, "")
  if (value === "" || !/^[A-Za-z0-9+/]+={0,2}$/.test(value)) return undefined
  try {
    return Uint8Array.from(atob(value), (character) => character.charCodeAt(0))
  } catch {
    return undefined
  }
}

export function constantTimeEqual(left: string, right: string): boolean {
  const a = new TextEncoder().encode(left)
  const b = new TextEncoder().encode(right)
  if (a.length !== b.length) return false
  let difference = 0
  for (let index = 0; index < a.length; index++) difference |= a[index]! ^ b[index]!
  return difference === 0
}

function encodeBase64(bytes: Uint8Array): string {
  let binary = ""
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return btoa(binary)
}

function integer(value: string | undefined): number | undefined {
  return value !== undefined && /^-?\d+$/.test(value) ? Number(value) : undefined
}
