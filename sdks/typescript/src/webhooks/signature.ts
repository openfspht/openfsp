import {
    encodeBase64,
    parseDictionary,
    serializeInnerList,
    type BareItem,
    type InnerList,
} from "./structured-fields.js"

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

export interface ReceivedSignature {
    readonly label: string
    readonly params: SignatureFields
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

export function selectSignature(header: string): ReceivedSignature | string {
    const members = parseDictionary(header)
    if (members === undefined) {
        return "malformed Signature-Input"
    }
    const tagged = [...members].filter(
        (entry): entry is [string, InnerList] =>
            entry[1].kind === "list" && isString(entry[1].parameters.get("tag"), TAG)
    )
    if (tagged.length !== 1) {
        return tagged.length === 0 ? `no ${TAG} signature` : `several ${TAG} signatures`
    }
    const [label, list] = tagged[0]!
    if (!sameComponents(list)) {
        return "covered components differ from the specification"
    }
    const created = integer(list, "created")
    const expires = integer(list, "expires")
    const keyId = string(list, "keyid")
    const nonce = string(list, "nonce")
    const alg = string(list, "alg")
    if (created === undefined || expires === undefined) {
        return "missing or malformed created or expires"
    }
    if (keyId === undefined || nonce === undefined || alg === undefined) {
        return "missing or malformed keyid, nonce or alg"
    }
    return {
        label,
        params: { created, expires, keyId, nonce, alg, tag: TAG },
        serialized: serializeInnerList(list),
    }
}

export function coveredComponents(serialized: string): readonly string[] | undefined {
    const member = parseDictionary(`${LABEL}=${serialized}`)?.get(LABEL)
    return member?.kind === "list"
        ? member.items.map((item) => (item.value.type === "string" ? item.value.value : ""))
        : undefined
}

export function signatureBase(
    message: SignedMessage,
    serialized: string,
    components: readonly string[] = COMPONENT_NAMES
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
            ...components.map((name) => `"${name}": ${values[name as ComponentName] ?? ""}`),
            `"@signature-params": ${serialized}`,
        ].join("\n")
    )
}

export async function sha256(body: Uint8Array<ArrayBuffer>): Promise<Uint8Array<ArrayBuffer>> {
    return new Uint8Array(await crypto.subtle.digest("SHA-256", body))
}

export async function contentDigest(body: Uint8Array<ArrayBuffer>): Promise<string> {
    return `sha-256=:${encodeBase64(await sha256(body))}:`
}

export function sha256Member(header: string): Uint8Array<ArrayBuffer> | undefined {
    return bytesMember(header, "sha-256")
}

export function formatSignature(signature: Uint8Array, label = LABEL): string {
    return `${label}=:${encodeBase64(signature)}:`
}

export function signatureBytes(header: string, label: string): Uint8Array<ArrayBuffer> | undefined {
    return bytesMember(header, label)
}

export function constantTimeEqual(left: Uint8Array, right: Uint8Array): boolean {
    if (left.length !== right.length) {
        return false
    }
    let difference = 0
    for (let index = 0; index < left.length; index++) {
        difference |= left[index]! ^ right[index]!
    }
    return difference === 0
}

function bytesMember(header: string, key: string): Uint8Array<ArrayBuffer> | undefined {
    const member = parseDictionary(header)?.get(key)
    return member?.kind === "item" && member.value.type === "bytes" ? member.value.value : undefined
}

function sameComponents(list: InnerList): boolean {
    return (
        list.items.length === COMPONENT_NAMES.length &&
        list.items.every(
            (item, index) =>
                item.parameters.size === 0 && isString(item.value, COMPONENT_NAMES[index]!)
        )
    )
}

function isString(item: BareItem | undefined, expected: string): boolean {
    return item?.type === "string" && item.value === expected
}

function integer(list: InnerList, name: string): number | undefined {
    const item = list.parameters.get(name)
    return item?.type === "integer" ? item.value : undefined
}

function string(list: InnerList, name: string): string | undefined {
    const item = list.parameters.get(name)
    return item?.type === "string" ? item.value : undefined
}
