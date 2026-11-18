import { InvalidSignatureError, UnknownEventTypeError } from "../errors.js"
import { isPaymentEventType, type PaymentEvent } from "../models/event.js"
import { isPaymentStatus } from "../models/payment.js"
import { toPaymentEvent } from "../protocol/mapping.js"
import { parseObject } from "../protocol/problem.js"
import type { WebhookKey } from "../models/configuration.js"
import type { WireEvent } from "../protocol/wire.js"
import { WebhookKeys, type KeySource } from "./keys.js"
import { InMemoryNonceStore, type NonceStore } from "./nonces.js"
import {
    ALGORITHM,
    constantTimeEqual,
    ECDSA_P256_SHA256,
    MAX_VALIDITY_SECONDS,
    selectSignature,
    sha256,
    sha256Member,
    signatureBase,
    signatureBytes,
    type ReceivedSignature,
    type SignatureFields,
} from "./signature.js"

export interface WebhookDelivery {
    readonly method: string
    readonly url: string
    readonly headers: Headers | Readonly<Record<string, string | readonly string[] | undefined>>
    /** The body exactly as received: a parsed and re-serialized body no longer matches its digest. */
    readonly body: Uint8Array | ArrayBuffer | string
}

export interface WebhookOptions {
    /** The public URL the server delivers to, when a proxy changes the URL the application sees. */
    readonly url?: string
    /** Shared by every instance that receives deliveries, so a replay is caught whichever instance gets it. */
    readonly nonceStore?: NonceStore
    readonly now?: () => number
}

const EVENT_FIELDS = ["id", "type", "created_at", "sequence", "data"] as const

export class Webhooks {
    private readonly keys: WebhookKeys
    private readonly nonces: NonceStore
    private readonly url: string | undefined
    private readonly now: () => number

    constructor(source: KeySource, options: WebhookOptions = {}) {
        this.now = options.now ?? (() => Math.floor(Date.now() / 1000))
        this.keys = new WebhookKeys(source, this.now)
        this.nonces = options.nonceStore ?? new InMemoryNonceStore(this.now)
        this.url = options.url
    }

    static withKeys(keys: readonly WebhookKey[], options: WebhookOptions = {}): Webhooks {
        const fixed = Object.freeze([...keys])
        return new Webhooks(async () => fixed, options)
    }

    /** A verified event can still be a redelivery: deduplicate by `event.id`. */
    async verify(delivery: Request | WebhookDelivery): Promise<PaymentEvent> {
        const received = delivery instanceof Request ? await fromRequest(delivery) : delivery
        const header = headerReader(received.headers)
        const body = toBytes(received.body)
        const signature = selectSignature(required(header, "signature-input"))
        if (typeof signature === "string") {
            throw new InvalidSignatureError(signature)
        }
        const { params } = signature
        const key = await this.keys.find(params.keyId)
        if (key === undefined) {
            throw new InvalidSignatureError(`unknown keyid ${params.keyId}`)
        }
        const algorithm = algorithmOf(key)
        if (algorithm === undefined || params.alg !== algorithm.wire) {
            throw new InvalidSignatureError("alg does not match the key")
        }
        this.checkFreshness(params)
        const message = await verifyMessage(
            received.method,
            this.url ?? received.url,
            header,
            body,
            signature,
            key,
            algorithm.verify
        )
        if (!(await this.nonces.reserve(params.nonce, params.expires))) {
            throw new InvalidSignatureError("nonce already used")
        }
        return toEvent(body, message)
    }

    private checkFreshness(params: SignatureFields): void {
        const now = this.now()
        if (params.created < 0 || params.expires < params.created) {
            throw new InvalidSignatureError("invalid validity window")
        }
        if (params.expires - params.created > MAX_VALIDITY_SECONDS) {
            throw new InvalidSignatureError("validity window too long")
        }
        if (now > params.expires) {
            throw new InvalidSignatureError("signature expired")
        }
        if (params.created > now + MAX_VALIDITY_SECONDS) {
            throw new InvalidSignatureError("signature created in the future")
        }
    }
}

interface VerifiedMessage {
    readonly eventId: string
    readonly eventType: string
}

async function verifyMessage(
    method: string,
    targetUri: string,
    header: (name: string) => string | undefined,
    body: Uint8Array<ArrayBuffer>,
    signature: ReceivedSignature,
    key: CryptoKey,
    algorithm: AlgorithmIdentifier | EcdsaParams
): Promise<VerifiedMessage> {
    const digest = required(header, "content-digest")
    const announced = sha256Member(digest)
    if (announced === undefined) {
        throw new InvalidSignatureError("Content-Digest has no sha-256 member")
    }
    if (!constantTimeEqual(announced, await sha256(body))) {
        throw new InvalidSignatureError("Content-Digest does not match the body")
    }
    const message = {
        method,
        targetUri,
        contentType: required(header, "content-type"),
        contentDigest: digest,
        eventId: required(header, "openfsp-event-id"),
        eventType: required(header, "openfsp-event-type"),
    }
    const bytes = signatureBytes(header("signature") ?? "", signature.label)
    if (bytes === undefined) {
        throw new InvalidSignatureError("missing or malformed Signature")
    }
    const base = signatureBase(message, signature.serialized)
    if (!(await crypto.subtle.verify(algorithm, key, bytes, base))) {
        throw new InvalidSignatureError("invalid signature")
    }
    return message
}

function toEvent(body: Uint8Array, message: VerifiedMessage): PaymentEvent {
    const event = parseObject(decode(body)) as WireEvent | undefined
    if (event === undefined) {
        throw new InvalidSignatureError("the body is not an event")
    }
    if (event.id !== message.eventId) {
        throw new InvalidSignatureError("event id does not match OpenFSP-Event-Id")
    }
    if (event.type !== message.eventType) {
        throw new InvalidSignatureError("event type does not match OpenFSP-Event-Type")
    }
    if (!isPaymentEventType(event.type)) {
        throw new UnknownEventTypeError(event.id, event.type)
    }
    if (EVENT_FIELDS.some((field) => event[field] === undefined)) {
        throw new InvalidSignatureError("the body is not an event")
    }
    if (!isSupported(event)) {
        throw new InvalidSignatureError("the body is not a supported event")
    }
    return toPaymentEvent(event)
}

function algorithmOf(
    key: CryptoKey
): { readonly wire: string; readonly verify: AlgorithmIdentifier | EcdsaParams } | undefined {
    const { name, namedCurve } = key.algorithm as EcKeyAlgorithm
    if (name === "Ed25519") {
        return { wire: ALGORITHM, verify: "Ed25519" }
    }
    if (name === "ECDSA" && namedCurve === "P-256") {
        return { wire: ECDSA_P256_SHA256, verify: { name: "ECDSA", hash: "SHA-256" } }
    }
    return undefined
}

function isSupported(event: WireEvent): boolean {
    return (
        typeof event.data === "object" &&
        event.data !== null &&
        isPaymentStatus(event.data.status) &&
        (event.previous_status === undefined ||
            event.previous_status === null ||
            isPaymentStatus(event.previous_status))
    )
}

async function fromRequest(request: Request): Promise<WebhookDelivery> {
    return {
        method: request.method,
        url: request.url,
        headers: request.headers,
        body: new Uint8Array(await request.arrayBuffer()),
    }
}

function required(header: (name: string) => string | undefined, name: string): string {
    const value = header(name)
    if (value === undefined) {
        throw new InvalidSignatureError(`missing ${name}`)
    }
    return value
}

function headerReader(headers: WebhookDelivery["headers"]): (name: string) => string | undefined {
    if (headers instanceof Headers) {
        return (name) => headers.get(name) ?? undefined
    }
    const lowered = new Map<string, string>()
    for (const [name, value] of Object.entries(headers)) {
        if (value !== undefined) {
            lowered.set(name.toLowerCase(), typeof value === "string" ? value : value.join(", "))
        }
    }
    return (name) => lowered.get(name)
}

function toBytes(body: WebhookDelivery["body"]): Uint8Array<ArrayBuffer> {
    if (typeof body === "string") {
        return new TextEncoder().encode(body)
    }
    return new Uint8Array(body instanceof ArrayBuffer ? body.slice(0) : body)
}

function decode(body: Uint8Array): string {
    try {
        return new TextDecoder("utf-8", { fatal: true }).decode(body)
    } catch {
        return ""
    }
}
