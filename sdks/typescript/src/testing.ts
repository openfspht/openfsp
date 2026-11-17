import type { Clock } from "./http/api.js"
import type { HttpRequest, HttpResponse, Transport } from "./http/transport.js"
import type { WebhookKey } from "./models/configuration.js"
import type { WireEvent, WirePayment } from "./protocol/wire.js"
import type { WebhookDelivery } from "./webhooks/webhooks.js"
import {
    ALGORITHM,
    COMPONENT_NAMES,
    contentDigest,
    formatSignature,
    LABEL,
    MAX_VALIDITY_SECONDS,
    parseParams,
    serializeParams,
    signatureBase,
    TAG,
} from "./webhooks/signature.js"

export type { Clock, HttpRequest, HttpResponse, Transport }

export function jsonResponse(
    status: number,
    body: unknown,
    headers: Readonly<Record<string, string>> = {}
): HttpResponse {
    const contentType = status >= 400 ? "application/problem+json" : "application/json"
    return {
        status,
        headers: new Headers({ "Content-Type": contentType, ...headers }),
        body: JSON.stringify(body),
    }
}

export function problemResponse(
    status: number,
    code: string,
    fields: Readonly<Record<string, unknown>> = {},
    headers: Readonly<Record<string, string>> = {}
): HttpResponse {
    return jsonResponse(
        status,
        {
            type: `https://openfsp.org/problems/${code}`,
            title: code,
            status,
            retryable: false,
            effect: "none",
            request_id: "req_test",
            ...fields,
        },
        headers
    )
}

export class FakeTransport implements Transport {
    readonly requests: HttpRequest[] = []
    private readonly replies: (HttpResponse | Error)[]

    constructor(...replies: (HttpResponse | Error)[]) {
        this.replies = replies
    }

    enqueue(...replies: (HttpResponse | Error)[]): this {
        this.replies.push(...replies)
        return this
    }

    async send(request: HttpRequest): Promise<HttpResponse> {
        this.requests.push(request)
        const reply = this.replies.shift()
        if (reply === undefined) {
            throw new Error(
                `FakeTransport has no reply left for ${request.method} ${request.url.pathname}`
            )
        }
        if (reply instanceof Error) {
            throw reply
        }
        return reply
    }
}

export class FakeClock implements Clock {
    readonly sleeps: number[] = []
    private current: number

    constructor(now: Date | number = Date.UTC(2026, 10, 15, 12)) {
        this.current = typeof now === "number" ? now : now.getTime()
    }

    now(): number {
        return this.current
    }

    async sleep(milliseconds: number): Promise<void> {
        this.sleeps.push(milliseconds)
        this.current += milliseconds
    }

    random(): number {
        return 1
    }

    advance(milliseconds: number): void {
        this.current += milliseconds
    }
}

export function wirePayment(overrides: Partial<WirePayment> = {}): WirePayment {
    return {
        id: "pay_test",
        reference: "INV-184",
        status: "pending",
        amount: { amount: 125000, currency: "HTG" },
        provider: "moncash",
        created_at: "2026-11-15T12:00:00Z",
        updated_at: "2026-11-15T12:00:00Z",
        ...overrides,
    }
}

export function wireEvent(overrides: Partial<WireEvent> = {}): WireEvent {
    const data =
        overrides.data ?? wirePayment({ status: "succeeded", completed_at: "2026-11-15T12:01:00Z" })
    return {
        id: "evt_test",
        type: `payment.${data.status === "pending" ? "created" : data.status}`,
        created_at: "2026-11-15T12:01:00Z",
        sequence: 2,
        resource_type: "payment",
        resource_id: data.id,
        reference: data.reference,
        previous_status: "pending",
        ...overrides,
        data,
    }
}

export interface SignOptions {
    readonly url: string
    readonly event?: WireEvent
    readonly created?: number
    readonly expires?: number
    readonly nonce?: string
    readonly signatureParams?: string
    readonly contentDigest?: string
}

export class WebhookSigner {
    readonly jwk: WebhookKey
    private readonly privateKey: CryptoKey

    private constructor(jwk: WebhookKey, privateKey: CryptoKey) {
        this.jwk = jwk
        this.privateKey = privateKey
    }

    static async generate(keyId = "key_test"): Promise<WebhookSigner> {
        const pair = (await crypto.subtle.generateKey({ name: "Ed25519" }, true, [
            "sign",
            "verify",
        ])) as CryptoKeyPair
        const { x } = await crypto.subtle.exportKey("jwk", pair.publicKey)
        return new WebhookSigner(
            {
                kid: keyId,
                kty: "OKP",
                crv: "Ed25519",
                alg: "EdDSA",
                use: "sig",
                x,
                openfsp_status: "active",
            },
            pair.privateKey
        )
    }

    async sign(options: SignOptions): Promise<
        WebhookDelivery & {
            readonly headers: Record<string, string>
            readonly body: Uint8Array<ArrayBuffer>
        }
    > {
        const event = options.event ?? wireEvent()
        const body = new TextEncoder().encode(JSON.stringify(event))
        const created = options.created ?? Math.floor(Date.now() / 1000)
        const fields = {
            created,
            expires: options.expires ?? created + MAX_VALIDITY_SECONDS,
            keyId: this.jwk.kid,
            nonce: options.nonce ?? crypto.randomUUID(),
            alg: ALGORITHM,
            tag: TAG,
        }
        const message = {
            method: "POST",
            targetUri: options.url,
            contentType: "application/json",
            contentDigest: options.contentDigest ?? (await contentDigest(body)),
            eventId: event.id,
            eventType: event.type,
        }
        const serialized = options.signatureParams ?? serializeParams(fields)
        const components = parseParams(`${LABEL}=${serialized}`)?.components ?? COMPONENT_NAMES
        const signature = new Uint8Array(
            await crypto.subtle.sign(
                "Ed25519",
                this.privateKey,
                signatureBase(message, { components, serialized })
            )
        )
        return {
            method: "POST",
            url: options.url,
            headers: {
                "Content-Type": message.contentType,
                "Content-Digest": message.contentDigest,
                "OpenFSP-Event-Id": event.id,
                "OpenFSP-Event-Type": event.type,
                "Signature-Input": `${LABEL}=${serialized}`,
                Signature: formatSignature(signature),
            },
            body,
        }
    }
}
