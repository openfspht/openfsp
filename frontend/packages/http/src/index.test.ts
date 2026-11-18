import { afterEach, describe, expect, mock, test } from "bun:test"

import {
    HttpError,
    NetworkError,
    post,
    query,
    remove,
    request,
    UnauthenticatedError,
} from "./index"

const realFetch = globalThis.fetch

const answer = (response: Response) => {
    const fetchMock = mock<(...args: Parameters<typeof fetch>) => Promise<Response>>(() =>
        Promise.resolve(response)
    )
    globalThis.fetch = fetchMock as unknown as typeof fetch
    return fetchMock
}

const json = (body: unknown, status: number) =>
    new Response(JSON.stringify(body), {
        status,
        headers: { "Content-Type": "application/problem+json" },
    })

afterEach(() => {
    globalThis.fetch = realFetch
})

describe("request", () => {
    test("sends JSON on the same origin and parses the answer", async () => {
        const fetchMock = answer(new Response('{"id":"pay_1"}', { status: 200 }))
        expect(await request<{ id: string }>("/api/payments", post({ amount: 1 }))).toEqual({
            id: "pay_1",
        })
        const [path, init] = fetchMock.mock.calls[0]
        expect(path).toBe("/api/payments")
        expect(init?.method).toBe("POST")
        expect(init?.body).toBe('{"amount":1}')
        expect(init?.credentials).toBe("same-origin")
        expect((init?.headers as Record<string, string>)["Content-Type"]).toBe("application/json")
    })

    test("an empty answer resolves to undefined", async () => {
        answer(new Response(null, { status: 204 }))
        expect(await request<void>("/api/keys/1", remove())).toBeUndefined()
    })

    test("a 401 is an unauthenticated error", async () => {
        answer(json({ title: "Unauthorized", status: 401 }, 401))
        const error = await request("/api/session").catch((caught: unknown) => caught)
        expect(error).toBeInstanceOf(UnauthenticatedError)
        expect(error).toBeInstanceOf(HttpError)
        expect((error as HttpError).status).toBe(401)
    })

    test("a problem keeps its status and names the first field error", async () => {
        answer(
            json(
                {
                    type: "https://openfsp.org/problems/invalid-request",
                    title: "Invalid request",
                    status: 422,
                    errors: [{ field: "amount", code: "too_small", detail: "must be positive" }],
                },
                422
            )
        )
        const error = (await request("/api/payments", post({})).catch(
            (caught) => caught
        )) as HttpError
        expect(error.status).toBe(422)
        expect(error.reason).toBe("amount: must be positive")
        expect(error.problem?.title).toBe("Invalid request")
    })

    test("a problem without field errors gives its detail, then its title", async () => {
        answer(json({ title: "Conflict", detail: "already revoked", status: 409 }, 409))
        expect(((await request("/api/keys").catch((caught) => caught)) as HttpError).reason).toBe(
            "already revoked"
        )
        answer(json({ title: "Conflict", status: 409 }, 409))
        expect(((await request("/api/keys").catch((caught) => caught)) as HttpError).reason).toBe(
            "Conflict"
        )
    })

    test("an answer without a problem has no reason", async () => {
        answer(new Response("<html>bad gateway</html>", { status: 502 }))
        const error = (await request("/api/keys").catch((caught) => caught)) as HttpError
        expect(error.status).toBe(502)
        expect(error.reason).toBeUndefined()
        expect(error.message).toBe("GET /api/keys answered 502")
    })

    test("an unreachable server is a network error", async () => {
        globalThis.fetch = mock(() =>
            Promise.reject(new TypeError("Failed to fetch"))
        ) as unknown as typeof fetch
        expect(await request("/api/keys").catch((caught) => caught)).toBeInstanceOf(NetworkError)
    })
})

describe("query", () => {
    test("drops empty values, repeats lists and encodes every value", () => {
        expect(query({ q: "a&b=c", page: 2, status: ["pending", "failed"], empty: "" })).toBe(
            "q=a%26b%3Dc&page=2&status=pending&status=failed"
        )
    })
})
