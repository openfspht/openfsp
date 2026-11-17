import { describe, expect, test } from "bun:test"
import {
    InvalidSignatureError,
    NO_RETRY,
    OpenFsp,
    TransportError,
    WebhookKeysUnavailableError,
    Webhooks,
} from "../src/index"
import type { WireEvent } from "../src/protocol/wire"
import { FakeTransport, jsonResponse, WebhookSigner, wireEvent } from "../src/testing"

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

    test("rejects a replay and releases the nonce of a forgery", async () => {
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
})
