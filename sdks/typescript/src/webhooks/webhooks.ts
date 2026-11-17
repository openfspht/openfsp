import { InvalidSignatureError } from "../errors.js"
import type { PaymentEvent } from "../models/event.js"
import { toPaymentEvent } from "../protocol/mapping.js"
import { parseObject } from "../protocol/problem.js"
import type { WireEvent, WireJwk } from "../protocol/wire.js"
import { WebhookKeys, type KeySource } from "./keys.js"
import { InMemoryNonceStore, type NonceStore } from "./nonces.js"
import {
    ALGORITHM,
    constantTimeEqual,
    contentDigest,
    MAX_VALIDITY_SECONDS,
    parseParams,
    parseSignature,
    signatureBase,
    TAG,
    type SignatureParams,
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

    static withKeys(keys: readonly WireJwk[], options: WebhookOptions = {}): Webhooks {
        const fixed = Object.freeze([...keys])
        return new Webhooks(async () => fixed, options)
    }

    /** Verifies before parsing (webhooks#5.9). A verified event can still be a redelivery: deduplicate by `event.id`. */
    async verify(delivery: Request | WebhookDelivery): Promise<PaymentEvent> {
        const received = delivery instanceof Request ? await fromRequest(delivery) : delivery
        const header = headerReader(received.headers)
        const body = toBytes(received.body)
        const params = parseParams(header("signature-input") ?? "")
        if (params === undefined) {
            throw new InvalidSignatureError("missing or malformed Signature-Input")
        }
        this.checkParams(params)
        const key = await this.keys.find(params.keyId)
        if (key === undefined) {
            throw new InvalidSignatureError(`unknown keyid ${params.keyId}`)
        }
        if (!(await this.nonces.reserve(params.nonce, params.expires))) {
            throw new InvalidSignatureError("nonce already used")
        }
        try {
            return await verifyMessage(
                received.method,
                this.url ?? received.url,
                header,
                body,
                params,
                key
            )
        } catch (failure) {
            await this.nonces.release(params.nonce)
            throw failure
        }
    }

    private checkParams(params: SignatureParams): void {
        const now = this.now()
        if (params.tag !== TAG) {
            throw new InvalidSignatureError("unexpected tag")
        }
        if (params.alg !== ALGORITHM) {
            throw new InvalidSignatureError("unsupported alg")
        }
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

async function verifyMessage(
    method: string,
    targetUri: string,
    header: (name: string) => string | undefined,
    body: Uint8Array<ArrayBuffer>,
    params: SignatureParams,
    key: CryptoKey
): Promise<PaymentEvent> {
    const digest = required(header, "content-digest")
    if (!constantTimeEqual(digest, await contentDigest(body))) {
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
    const signature = parseSignature(header("signature") ?? "")
    if (signature === undefined) {
        throw new InvalidSignatureError("missing or malformed Signature")
    }
    if (!(await crypto.subtle.verify("Ed25519", key, signature, signatureBase(message, params)))) {
        throw new InvalidSignatureError("invalid signature")
    }

    const event = parseObject(decode(body)) as WireEvent | undefined
    if (event === undefined || EVENT_FIELDS.some((field) => event[field] === undefined)) {
        throw new InvalidSignatureError("the body is not an event")
    }
    if (event.id !== message.eventId) {
        throw new InvalidSignatureError("event id does not match OpenFSP-Event-Id")
    }
    if (event.type !== message.eventType) {
        throw new InvalidSignatureError("event type does not match OpenFSP-Event-Type")
    }
    return toPaymentEvent(event)
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
