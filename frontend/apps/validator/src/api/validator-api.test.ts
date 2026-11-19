import { afterEach, describe, expect, test } from "bun:test"

import { NetworkError } from "@workspace/http"

import { problemOf, validatorApi } from "./validator-api"

const realFetch = globalThis.fetch

const answer = (response: Response | Error) => {
    globalThis.fetch = (() =>
        response instanceof Error
            ? Promise.reject(response)
            : Promise.resolve(response)) as unknown as typeof fetch
}

afterEach(() => {
    globalThis.fetch = realFetch
})

describe("validatorApi", () => {
    test("exposes the problem a refused request answers, its pointers included", async () => {
        answer(
            new Response(
                JSON.stringify({
                    type: "https://validate.openfsp.org/problems/invalid-request",
                    title: "Invalid request",
                    status: 400,
                    errors: [{ pointer: "/target", detail: "unknown target" }],
                }),
                { status: 400, headers: { "Content-Type": "application/problem+json" } }
            )
        )
        const error = await validatorApi.suite().catch((caught: unknown) => caught)
        const problem = problemOf(error)
        expect(problem?.status).toBe(400)
        expect(problem?.type?.endsWith("/invalid-request")).toBe(true)
        expect(problem?.errors?.[0]?.pointer).toBe("/target")
    })

    test("keeps the status of an answer without a problem and nothing for an unreachable server", async () => {
        answer(new Response("<html>Bad Gateway</html>", { status: 502 }))
        expect(problemOf(await validatorApi.suite().catch((caught: unknown) => caught))).toEqual({
            status: 502,
        })
        answer(new TypeError("fetch failed"))
        const unreachable = await validatorApi.suite().catch((caught: unknown) => caught)
        expect(unreachable).toBeInstanceOf(NetworkError)
        expect(problemOf(unreachable)).toBeUndefined()
    })
})
