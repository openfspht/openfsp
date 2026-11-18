import { describe, expect, test } from "bun:test"
import {
    InvalidSignatureError,
    NO_RETRY,
    OpenFsp,
    PAYMENT_EVENT_TYPES,
    TransportError,
    UnknownEventTypeError,
    WebhookKeysUnavailableError,
    Webhooks,
} from "../src/index"
import type { WireEvent } from "../src/protocol/wire"
import { FakeTransport, jsonResponse, WebhookSigner, wireEvent } from "../src/testing"
import { COMPONENTS } from "../src/webhooks/signature"

const wireConfiguration = (keys: unknown[]) =>
    jsonResponse(200, {
        issuer: "https://server.example",
        protocol_version: "0.1",
        capabilities_supported: ["payments"],
        webhook_keys: keys,
    })

const NOW = 1_800_000_000
const URL = "https://shop.example/openfsp/webhooks"
const signer = await WebhookSigner.generate("key-1")
const webhooks = () => Webhooks.withKeys([signer.jwk], { now: () => NOW })

async function reason(promise: Promise<unknown>): Promise<string> {
    const failure = await promise.catch((error: unknown) => error)
    expect(failure).toBeInstanceOf(InvalidSignatureError)
    return (failure as Error).message
}

describe("Webhooks.verify", () => {
    test("returns the event of a signed delivery", async () => {
        const event = await webhooks().verify(await signer.sign({ url: URL, created: NOW }))
        expect(event.type).toBe("payment.succeeded")
        expect(event.payment.isSuccessful).toBe(true)
        expect(event.reference).toBe("INV-184")
    })

    test("accepts a fetch Request", async () => {
        const delivery = await signer.sign({ url: URL, created: NOW })
        const request = new Request(URL, {
            method: "POST",
            headers: delivery.headers,
            body: delivery.body,
        })
        expect((await webhooks().verify(request)).id).toBe("evt_test")
    })

    test("verifies against the public URL behind a proxy", async () => {
        const delivery = await signer.sign({ url: URL, created: NOW })
        const proxied = Webhooks.withKeys([signer.jwk], { now: () => NOW, url: URL })
        expect(
            (await proxied.verify({ ...delivery, url: "http://10.0.0.4:3000/openfsp/webhooks" })).id
        ).toBe("evt_test")
    })

    test("rejects a replay and keeps the nonce of a forgery unused", async () => {
        const receiver = webhooks()
        const delivery = await signer.sign({ url: URL, created: NOW, nonce: "n-1" })
        await receiver.verify(delivery)
        expect(await reason(receiver.verify(delivery))).toBe("nonce already used")

        const forged = await signer.sign({ url: URL, created: NOW, nonce: "n-2" })
        expect(
            await reason(
                receiver.verify({
                    ...forged,
                    headers: { ...forged.headers, Signature: `sig1=:${btoa("x".repeat(64))}:` },
                })
            )
        ).toBe("invalid signature")
        await receiver.verify(forged)
    })

    test.each([
        ["an expired signature", { created: NOW - 600 }, "signature expired"],
        ["a signature from the future", { created: NOW + 900 }, "signature created in the future"],
        [
            "a long validity window",
            { created: NOW, expires: NOW + 3600 },
            "validity window too long",
        ],
    ])("rejects %s", async (_, options, message) => {
        expect(await reason(webhooks().verify(await signer.sign({ url: URL, ...options })))).toBe(
            message
        )
    })

    test("rejects a tampered body, an unknown key and a header that lies", async () => {
        const delivery = await signer.sign({ url: URL, created: NOW })
        expect(
            await reason(
                webhooks().verify({
                    ...delivery,
                    body: JSON.stringify({ ...wireEvent(), sequence: 9 }),
                })
            )
        ).toBe("Content-Digest does not match the body")
        expect(await reason(Webhooks.withKeys([], { now: () => NOW }).verify(delivery))).toBe(
            "unknown keyid key-1"
        )
        const other = await signer.sign({
            url: URL,
            created: NOW,
            event: wireEvent({ id: "evt_other" }),
        })
        expect(
            await reason(
                webhooks().verify({
                    ...other,
                    headers: { ...other.headers, "OpenFSP-Event-Id": "evt_test" },
                })
            )
        ).toBe("invalid signature")
    })

    test("loads keys from the server and refreshes them at most every 10 seconds", async () => {
        let now = NOW
        const configuration = (keys: unknown[]) =>
            jsonResponse(200, {
                issuer: "https://server.example",
                protocol_version: "0.1",
                capabilities_supported: [],
                webhook_keys: keys,
            })
        const transport = new FakeTransport(configuration([]), configuration([signer.jwk]))
        const openfsp = new OpenFsp(
            { baseUrl: "https://server.example", apiKey: "k" },
            { transport, webhooks: { now: () => now } }
        )

        await reason(openfsp.webhooks.verify(await signer.sign({ url: URL, created: now })))
        await reason(openfsp.webhooks.verify(await signer.sign({ url: URL, created: now })))
        expect(transport.requests).toHaveLength(1)

        now += 11
        expect(
            (await openfsp.webhooks.verify(await signer.sign({ url: URL, created: now }))).id
        ).toBe("evt_test")
        expect(transport.requests).toHaveLength(2)
    })

    test("reports keys it cannot load as unavailable, not as a bad signature", async () => {
        const openfsp = new OpenFsp(
            { baseUrl: "https://server.example", apiKey: "k", retryPolicy: NO_RETRY },
            {
                transport: new FakeTransport(new TransportError("connection refused")),
                webhooks: { now: () => NOW },
            }
        )
        const failure = await openfsp.webhooks
            .verify(await signer.sign({ url: URL, created: NOW }))
            .catch((error: unknown) => error)
        expect(failure).toBeInstanceOf(WebhookKeysUnavailableError)
        expect((failure as Error).cause).toBeInstanceOf(TransportError)
    })

    test("rejects a signed body it cannot read as a payment event", async () => {
        const event = wireEvent()
        const unknownStatus = {
            ...event,
            data: { ...event.data, status: "refunded" },
        } as unknown as WireEvent
        expect(
            await reason(
                webhooks().verify(
                    await signer.sign({ url: URL, created: NOW, event: unknownStatus })
                )
            )
        ).toBe("the body is not a supported event")
        const unknownPrevious = { ...event, previous_status: "on_hold" } as unknown as WireEvent
        expect(
            await reason(
                webhooks().verify(
                    await signer.sign({ url: URL, created: NOW, event: unknownPrevious })
                )
            )
        ).toBe("the body is not a supported event")
        expect(PAYMENT_EVENT_TYPES).toContain("payment.succeeded")
    })

    test("verifies an event of an unknown type and reports it apart, to be acknowledged", async () => {
        const receiver = webhooks()
        const refund = {
            id: "evt_refund",
            type: "refund.created",
            created_at: "2026-11-15T12:01:00Z",
            sequence: 1,
            resource_type: "refund",
            resource_id: "ref_1",
            reference: "INV-184",
            data: { id: "ref_1" },
        } as unknown as WireEvent
        const delivery = await signer.sign({ url: URL, created: NOW, event: refund })
        const failure = await receiver.verify(delivery).catch((error: unknown) => error)
        expect(failure).toBeInstanceOf(UnknownEventTypeError)
        expect(failure).not.toBeInstanceOf(InvalidSignatureError)
        expect((failure as UnknownEventTypeError).eventType).toBe("refund.created")
        expect((failure as UnknownEventTypeError).eventId).toBe("evt_refund")
        expect(await reason(receiver.verify(delivery))).toBe("nonce already used")

        const forged = {
            ...delivery,
            headers: { ...delivery.headers, Signature: `sig1=:${btoa("x".repeat(64))}:` },
        }
        expect(await reason(webhooks().verify(forged))).toBe("invalid signature")
    })

    test("retries a failed key refresh after a short backoff", async () => {
        let now = NOW
        const transport = new FakeTransport(
            new TransportError("connection refused"),
            wireConfiguration([signer.jwk])
        )
        const openfsp = new OpenFsp(
            { baseUrl: "https://server.example", apiKey: "k", retryPolicy: NO_RETRY },
            { transport, webhooks: { now: () => now } }
        )
        const attempt = async () =>
            openfsp.webhooks
                .verify(await signer.sign({ url: URL, created: now }))
                .catch((error: unknown) => error)

        expect(await attempt()).toBeInstanceOf(WebhookKeysUnavailableError)
        expect(await attempt()).toBeInstanceOf(WebhookKeysUnavailableError)
        expect(transport.requests).toHaveLength(1)

        now += 2
        expect(((await attempt()) as { id: string }).id).toBe("evt_test")
        expect(transport.requests).toHaveLength(2)
    })

    test("verifies the signature parameters exactly as the signer sent them", async () => {
        const signatureParams = `${COMPONENTS};keyid="key-1";alg="ed25519";created=${NOW};expires=${NOW + 300};nonce="n-order";tag="openfsp-webhook"`
        const delivery = await signer.sign({ url: URL, created: NOW, signatureParams })
        expect((await webhooks().verify(delivery)).id).toBe("evt_test")
    })

    test.each([
        [
            "components in another order",
            '("openfsp-event-type" "@method" "@target-uri" "content-type" "content-digest" "openfsp-event-id")',
            "openfsp-webhook",
            "covered components differ from the specification",
        ],
        [
            "fewer components",
            '("@method" "@target-uri" "content-digest" "openfsp-event-id" "openfsp-event-type")',
            "openfsp-webhook",
            "covered components differ from the specification",
        ],
        ["another tag", COMPONENTS, "other", "no openfsp-webhook signature"],
    ])("rejects %s even when signed", async (_, components, tag, message) => {
        const signatureParams = `${components};created=${NOW};expires=${NOW + 300};keyid="key-1";nonce="n-strict";alg="ed25519";tag="${tag}"`
        const delivery = await signer.sign({ url: URL, created: NOW, signatureParams })
        expect(await reason(webhooks().verify(delivery))).toBe(message)
    })

    test("rejects a header without a label or two openfsp signatures", async () => {
        const delivery = await signer.sign({ url: URL, created: NOW })
        const input = delivery.headers["Signature-Input"] ?? ""
        const unlabeled = { ...delivery.headers, "Signature-Input": input.replace(/^sig1=/, "") }
        expect(await reason(webhooks().verify({ ...delivery, headers: unlabeled }))).toBe(
            "malformed Signature-Input"
        )
        const doubled = {
            ...delivery.headers,
            "Signature-Input": `${input}, ${input.replace(/^sig1=/, "sig2=")}`,
        }
        expect(await reason(webhooks().verify({ ...delivery, headers: doubled }))).toBe(
            "several openfsp-webhook signatures"
        )
    })

    test("resolves the keyid before checking anything else", async () => {
        const delivery = await signer.sign({ url: URL, created: NOW - 3600 })
        expect(await reason(Webhooks.withKeys([], { now: () => NOW }).verify(delivery))).toBe(
            "unknown keyid key-1"
        )
    })

    test("picks its own member among several signatures and digests", async () => {
        const sha256 = (await signer.sign({ url: URL, created: NOW })).headers["Content-Digest"]
        const digest = `sha-512=:${btoa("x".repeat(64))}:, ${sha256}`
        const delivery = await signer.sign({
            url: URL,
            created: NOW,
            nonce: "n-many",
            contentDigest: digest,
        })
        const headers = {
            ...delivery.headers,
            "Signature-Input": `proxy=("@method");created=${NOW};keyid="proxy", ${delivery.headers["Signature-Input"]}`,
            Signature: `proxy=:${btoa("y".repeat(64))}:, ${delivery.headers.Signature}`,
        }
        expect((await webhooks().verify({ ...delivery, headers })).id).toBe("evt_test")
    })

    test("verifies an ECDSA P-256 signature with an ES256 key", async () => {
        const p256 = await WebhookSigner.generate("key-ec", "ecdsa-p256-sha256")
        const receiver = Webhooks.withKeys([p256.jwk], { now: () => NOW })
        expect((await receiver.verify(await p256.sign({ url: URL, created: NOW }))).id).toBe(
            "evt_test"
        )
    })

    test("lets the key decide the algorithm", async () => {
        const p256 = await WebhookSigner.generate("key-1", "ecdsa-p256-sha256")
        const delivery = await p256.sign({ url: URL, created: NOW })
        expect(await reason(webhooks().verify(delivery))).toBe("alg does not match the key")
    })

    test.each([
        ["an ES256 alg on an Ed25519 key", { alg: "ES256" }],
        ["an encryption key", { use: "enc" }],
        ["a y coordinate on an OKP key", { y: "AAAA" }],
    ])("ignores %s", async (_, change) => {
        const delivery = await signer.sign({ url: URL, created: NOW })
        const receiver = Webhooks.withKeys([{ ...signer.jwk, ...change }], { now: () => NOW })
        expect(await reason(receiver.verify(delivery))).toBe("unknown keyid key-1")
    })
})
