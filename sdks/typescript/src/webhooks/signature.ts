export const COMPONENT_NAMES = Object.freeze([
    "@method",
    "@target-uri",
    "content-type",
    "content-digest",
    "openfsp-event-id",
    "openfsp-event-type",
] as const)
export const COMPONENTS = `(${COMPONENT_NAMES.map((name) => `"${name}"`).join(" ")})`
export const LABEL = "sig1"
export const TAG = "openfsp-webhook"
export const ALGORITHM = "ed25519"
export const MAX_VALIDITY_SECONDS = 300

type ComponentName = (typeof COMPONENT_NAMES)[number]

export interface SignatureFields {
    readonly created: number
    readonly expires: number
    readonly keyId: string
    readonly nonce: string
    readonly alg: string
    readonly tag: string
}

export interface SignatureParams extends SignatureFields {
    readonly components: readonly ComponentName[]
    readonly serialized: string
}

export interface SignedMessage {
    readonly method: string
    readonly targetUri: string
    readonly contentType: string
    readonly contentDigest: string
    readonly eventId: string
    readonly eventType: string
}

export function serializeParams(fields: SignatureFields): string {
    return `${COMPONENTS};created=${fields.created};expires=${fields.expires};keyid="${fields.keyId}";nonce="${fields.nonce}";alg="${fields.alg}";tag="${fields.tag}"`
}

export function parseParams(header: string): SignatureParams | undefined {
    const serialized = dictionary(header).get(LABEL)
    if (serialized === undefined || !serialized.startsWith("(")) {
        return undefined
    }
    const close = serialized.indexOf(")")
    const components = close < 0 ? undefined : parseComponents(serialized.slice(1, close))
    if (components === undefined) {
        return undefined
    }
    const fields = new Map<string, string>()
    for (const part of split(serialized.slice(close + 1), ";")) {
        if (part === "") {
            continue
        }
        const separator = part.indexOf("=")
        if (separator < 0) {
            return undefined
        }
        fields.set(part.slice(0, separator), unquote(part.slice(separator + 1)))
    }
    const created = integer(fields.get("created"))
    const expires = integer(fields.get("expires"))
    const keyId = fields.get("keyid")
    const nonce = fields.get("nonce")
    const alg = fields.get("alg")
    const tag = fields.get("tag")
    if (created === undefined || expires === undefined || !keyId || !nonce || !alg || !tag) {
        return undefined
    }
    return { created, expires, keyId, nonce, alg, tag, components, serialized }
}

export function signatureBase(
    message: SignedMessage,
    params: Pick<SignatureParams, "components" | "serialized">
): Uint8Array<ArrayBuffer> {
    const values: Record<ComponentName, string> = {
        "@method": message.method.toUpperCase(),
        "@target-uri": message.targetUri,
        "content-type": message.contentType,
        "content-digest": message.contentDigest,
        "openfsp-event-id": message.eventId,
        "openfsp-event-type": message.eventType,
    }
    return new TextEncoder().encode(
        [
            ...params.components.map((name) => `"${name}": ${values[name]}`),
            `"@signature-params": ${params.serialized}`,
        ].join("\n")
    )
}

export function parseContentDigest(header: string): string | undefined {
    return dictionary(header).get("sha-256")
}

export async function contentDigest(body: Uint8Array<ArrayBuffer>): Promise<string> {
    return `sha-256=:${encodeBase64(new Uint8Array(await crypto.subtle.digest("SHA-256", body)))}:`
}

export function formatSignature(signature: Uint8Array): string {
    return `${LABEL}=:${encodeBase64(signature)}:`
}

export function parseSignature(header: string): Uint8Array<ArrayBuffer> | undefined {
    const member = dictionary(header).get(LABEL)
    const value = member?.startsWith(":") && member.endsWith(":") ? member.slice(1, -1) : ""
    if (value === "" || !/^[A-Za-z0-9+/]+={0,2}$/.test(value)) {
        return undefined
    }
    try {
        return Uint8Array.from(atob(value), (character) => character.charCodeAt(0))
    } catch {
        return undefined
    }
}

export function constantTimeEqual(left: string, right: string): boolean {
    const a = new TextEncoder().encode(left)
    const b = new TextEncoder().encode(right)
    if (a.length !== b.length) {
        return false
    }
    let difference = 0
    for (let index = 0; index < a.length; index++) {
        difference |= a[index]! ^ b[index]!
    }
    return difference === 0
}

function encodeBase64(bytes: Uint8Array): string {
    let binary = ""
    for (const byte of bytes) {
        binary += String.fromCharCode(byte)
    }
    return btoa(binary)
}

function parseComponents(list: string): ComponentName[] | undefined {
    const names = list.trim() === "" ? [] : list.trim().split(/ +/).map(unquote)
    const required: readonly string[] = COMPONENT_NAMES
    if (
        names.length !== required.length ||
        new Set(names).size !== names.length ||
        names.some((name) => !required.includes(name))
    ) {
        return undefined
    }
    return names as ComponentName[]
}

function dictionary(header: string): Map<string, string> {
    const members = new Map<string, string>()
    for (const member of split(header, ",")) {
        const separator = member.indexOf("=")
        if (separator > 0 && !members.has(member.slice(0, separator))) {
            members.set(member.slice(0, separator), member.slice(separator + 1))
        }
    }
    return members
}

function split(value: string, separator: "," | ";"): string[] {
    const parts: string[] = []
    let current = ""
    let quoted = false
    let depth = 0
    for (let index = 0; index < value.length; index++) {
        const character = value[index]!
        if (quoted && character === "\\") {
            current += character + (value[++index] ?? "")
            continue
        }
        if (character === '"') {
            quoted = !quoted
        } else if (!quoted && character === "(") {
            depth++
        } else if (!quoted && character === ")") {
            depth--
        } else if (!quoted && depth === 0 && character === separator) {
            parts.push(current.trim())
            current = ""
            continue
        }
        current += character
    }
    parts.push(current.trim())
    return parts
}

function unquote(value: string): string {
    return /^".*"$/.test(value) ? value.slice(1, -1).replace(/\\(["\\])/g, "$1") : value
}

function integer(value: string | undefined): number | undefined {
    return value !== undefined && /^-?\d+$/.test(value) ? Number(value) : undefined
}
