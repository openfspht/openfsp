import { describe, expect, test } from "bun:test"
import { InvalidRequestError, OpenFsp, type StatementQuery } from "../src/index"
import { FakeTransport, jsonResponse, wireStatementEntry } from "../src/testing"

function client(transport: FakeTransport): OpenFsp {
    return new OpenFsp({ baseUrl: "https://server.example", apiKey: "k" }, { transport })
}

describe("Statements", () => {
    test("lists the entries of a business day", async () => {
        const transport = new FakeTransport(
            jsonResponse(200, {
                data: [
                    wireStatementEntry({
                        provider: "natcash",
                        business_date: "2026-09-28",
                        booked_at: "2026-09-28T14:33:02Z",
                        fee: { amount: { amount: 2500, currency: "HTG" }, bearer: "merchant" },
                    }),
                ],
            })
        )
        const page = await client(transport).statements.list({
            provider: "natcash",
            businessDate: "2026-09-28",
        })

        expect(transport.requests[0]!.url.pathname).toBe("/v1/statements")
        expect(transport.requests[0]!.url.search).toBe("?provider=natcash&business_date=2026-09-28")
        expect(page.nextCursor).toBeUndefined()
        expect(page.entries).toHaveLength(1)
        const entry = page.entries[0]!
        expect(entry.provider).toBe("natcash")
        expect(entry.businessDate).toBe("2026-09-28")
        expect(entry.bookedAt).toEqual(new Date("2026-09-28T14:33:02Z"))
        expect(entry.amount.centimes).toBe(125000)
        expect(entry.fee?.amount.centimes).toBe(2500)
        expect(entry.fee?.bearer).toBe("merchant")
        expect(entry.paymentId).toBe("pay_test")
        expect(entry.reference).toBe("INV-184")
        expect(Object.isFrozen(page)).toBe(true)
        expect(Object.isFrozen(page.entries)).toBe(true)
        expect(Object.isFrozen(entry)).toBe(true)
    })

    test("follows a range page by page", async () => {
        const transport = new FakeTransport(
            jsonResponse(200, { data: [wireStatementEntry()], next_cursor: "c2" }),
            jsonResponse(200, { data: [] })
        )
        const statements = client(transport).statements
        const range = { provider: "natcash", from: "2026-09-01", to: "2026-09-30" } as const

        const first = await statements.list({ ...range, limit: 1 })
        expect(first.nextCursor).toBe("c2")
        const second = await statements.list({ ...range, limit: 1, cursor: first.nextCursor })
        expect(second.entries).toEqual([])
        expect(second.nextCursor).toBeUndefined()

        expect(transport.requests[0]!.url.search).toBe(
            "?provider=natcash&from=2026-09-01&to=2026-09-30&limit=1"
        )
        expect(transport.requests[1]!.url.searchParams.get("cursor")).toBe("c2")
    })

    test("leaves out what the server does not know", async () => {
        const transport = new FakeTransport(
            jsonResponse(200, {
                data: [wireStatementEntry({ payment: null, reference: undefined })],
            })
        )
        const entry = (
            await client(transport).statements.list({
                provider: "moncash",
                businessDate: "2026-11-15",
            })
        ).entries[0]!
        expect(entry.paymentId).toBeUndefined()
        expect(entry.reference).toBeUndefined()
        expect(entry.fee).toBeUndefined()
        expect("fee" in entry).toBe(false)
    })

    test("keeps a zero fee apart from an unknown one", async () => {
        const transport = new FakeTransport(
            jsonResponse(200, {
                data: [
                    wireStatementEntry({
                        fee: { amount: { amount: 0, currency: "HTG" }, bearer: "payer" },
                    }),
                    wireStatementEntry({ fee: null }),
                ],
            })
        )
        const [free, unknown] = (
            await client(transport).statements.list({
                provider: "moncash",
                businessDate: "2026-11-15",
            })
        ).entries
        expect(free!.fee?.amount.centimes).toBe(0)
        expect(free!.fee?.bearer).toBe("payer")
        expect(unknown!.fee).toBeUndefined()
    })

    test("refuses an entry without its required fields", async () => {
        const transport = new FakeTransport(
            jsonResponse(200, { data: [{ provider: "moncash", business_date: "2026-11-15" }] })
        )
        expect(
            client(transport).statements.list({ provider: "moncash", businessDate: "2026-11-15" })
        ).rejects.toMatchObject({ code: "unexpected-response" })
    })

    test.each([
        ["an empty provider", { provider: " ", businessDate: "2026-09-28" }],
        ["no period", { provider: "natcash" }],
        [
            "a day and a range",
            {
                provider: "natcash",
                businessDate: "2026-09-28",
                from: "2026-09-01",
                to: "2026-09-02",
            },
        ],
        ["half a range", { provider: "natcash", from: "2026-09-01" }],
        ["a malformed date", { provider: "natcash", businessDate: "28/09/2026" }],
        ["a date that does not exist", { provider: "natcash", businessDate: "2026-02-30" }],
        ["a reversed range", { provider: "natcash", from: "2026-09-02", to: "2026-09-01" }],
        ["a range over 31 days", { provider: "natcash", from: "2026-09-01", to: "2026-10-02" }],
        ["a limit of zero", { provider: "natcash", businessDate: "2026-09-28", limit: 0 }],
        ["a limit over 100", { provider: "natcash", businessDate: "2026-09-28", limit: 101 }],
        ["a fractional limit", { provider: "natcash", businessDate: "2026-09-28", limit: 1.5 }],
        ["an empty cursor", { provider: "natcash", businessDate: "2026-09-28", cursor: "" }],
    ])("refuses %s before calling", async (_name, query) => {
        const transport = new FakeTransport()
        expect(client(transport).statements.list(query as StatementQuery)).rejects.toBeInstanceOf(
            InvalidRequestError
        )
        expect(transport.requests).toHaveLength(0)
    })

    test("accepts a range of exactly 31 days", async () => {
        const transport = new FakeTransport(jsonResponse(200, { data: [] }))
        await client(transport).statements.list({
            provider: "natcash",
            from: "2026-10-01",
            to: "2026-10-31",
        })
        expect(transport.requests).toHaveLength(1)
    })
})
