import { describe, expect, test } from "bun:test"
import { ApiError, OpenFsp } from "../src/index"
import { FakeClock, FakeTransport, jsonResponse } from "../src/testing"

const PROVIDER = {
    id: "moncash",
    display_name: "MonCash",
    capabilities: ["payments.redirect"],
    payments: {
        next_actions: ["redirect"],
        payer_required: false,
        return_url_required: true,
        expiry_guaranteed: true,
        locales: ["fr"],
    },
}

describe("OpenFsp", () => {
    test("lists providers and keeps them for a minute", async () => {
        const capabilities = () =>
            jsonResponse(200, { protocol_version: "0.1", providers: [PROVIDER] })
        const clock = new FakeClock()
        const transport = new FakeTransport(capabilities(), capabilities())
        const openfsp = new OpenFsp(
            { baseUrl: "https://server.example", apiKey: "k" },
            { transport, clock }
        )
        expect((await openfsp.providers()).map((provider) => provider.displayName)).toEqual([
            "MonCash",
        ])
        expect((await openfsp.provider("moncash"))?.displayName).toBe("MonCash")
        expect(await openfsp.provider("natcash")).toBeUndefined()
        expect(transport.requests).toHaveLength(1)

        clock.advance(61_000)
        await openfsp.providers()
        expect(transport.requests).toHaveLength(2)
    })

    test("keeps providers for the lifetime the server announces", async () => {
        const capabilities = () =>
            jsonResponse(
                200,
                { protocol_version: "0.1", providers: [PROVIDER] },
                { "Cache-Control": "private, max-age=600" }
            )
        const clock = new FakeClock()
        const transport = new FakeTransport(capabilities(), capabilities())
        const openfsp = new OpenFsp(
            { baseUrl: "https://server.example", apiKey: "k" },
            { transport, clock }
        )
        await openfsp.providers()
        clock.advance(599_000)
        await openfsp.providers()
        expect(transport.requests).toHaveLength(1)

        clock.advance(2_000)
        await openfsp.providers()
        expect(transport.requests).toHaveLength(2)
    })

    test("finds the statement of a provider for a business date", async () => {
        const statement = {
            id: "stm_1",
            provider: "moncash",
            business_date: "2026-11-16",
            complete: true,
            entries: [
                {
                    type: "payment",
                    amount: { amount: 125000, currency: "HTG" },
                    booked_at: "2026-11-16T15:04:05Z",
                    payment: "pay_1",
                    match: "matched",
                },
            ],
            missing_payments: [],
        }
        const transport = new FakeTransport(
            jsonResponse(200, { data: [statement] }),
            jsonResponse(200, { data: [] })
        )
        const openfsp = new OpenFsp(
            { baseUrl: "https://server.example", apiKey: "k" },
            { transport }
        )
        const found = await openfsp.statements.find("moncash", "2026-11-16")
        expect(found?.entries[0]?.paymentId).toBe("pay_1")
        expect(found?.entries[0]?.amount.centimes).toBe(125000)
        expect(transport.requests[0]!.url.search).toBe("?provider=moncash&business_date=2026-11-16")
        expect(await openfsp.statements.find("moncash", "2026-11-17")).toBeUndefined()
    })

    test("reads the configuration document without credentials", async () => {
        const transport = new FakeTransport(
            jsonResponse(200, {
                issuer: "https://server.example",
                protocol_version: "0.1",
                capabilities_supported: ["payments", "statements"],
                statement_entries_endpoint: "https://server.example/v1/statement-entries",
                webhook_keys: [{ kid: "key-1", kty: "OKP", openfsp_status: "active" }],
            })
        )
        const configuration = await new OpenFsp(
            { baseUrl: "https://server.example", apiKey: "k" },
            { transport }
        ).configuration()
        expect(configuration.statementEntriesEndpoint).toBe(
            "https://server.example/v1/statement-entries"
        )
        expect(configuration.capabilitiesSupported).toEqual(["payments", "statements"])
        expect(configuration.webhookKeys.map((key) => key.kid)).toEqual(["key-1"])
        expect(transport.requests[0]!.url.pathname).toBe("/.well-known/openfsp-configuration")
        expect(transport.requests[0]!.headers.has("authorization")).toBe(false)
    })

    test("reads a minimal configuration document", async () => {
        const transport = new FakeTransport(
            jsonResponse(200, {
                issuer: "https://server.example",
                protocol_version: "0.1",
                capabilities_supported: ["payments"],
            })
        )
        const configuration = await new OpenFsp(
            { baseUrl: "https://server.example", apiKey: "k" },
            { transport }
        ).configuration()
        expect(configuration.protocolVersion).toBe("0.1")
        expect(configuration.paymentsEndpoint).toBeUndefined()
        expect(configuration.webhookKeys).toEqual([])
        expect(configuration.scopesSupported).toEqual([])
    })

    test("rejects a configuration document without its required members", async () => {
        const transport = new FakeTransport(
            jsonResponse(200, { issuer: "https://server.example", protocol_version: "0.1" })
        )
        const failure = await new OpenFsp(
            { baseUrl: "https://server.example", apiKey: "k" },
            { transport }
        )
            .configuration()
            .catch((error: unknown) => error)
        expect((failure as ApiError).code).toBe("unexpected-response")
    })

    test("is built from the environment and never shows its key", () => {
        const openfsp = OpenFsp.fromEnv({
            OPENFSP_BASE_URL: "https://server.example",
            OPENFSP_API_KEY: "ofsp_live_secret",
        })
        expect(String(openfsp)).toBe("OpenFsp(https://server.example/)")
        expect(JSON.stringify(openfsp)).not.toContain("secret")
    })
})
