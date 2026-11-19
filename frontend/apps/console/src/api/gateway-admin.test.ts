import { afterEach, describe, expect, mock, test } from "bun:test"

import { gatewayAdmin } from "./gateway-admin"

const realFetch = globalThis.fetch

const recordPaths = () => {
    const fetchMock = mock<(...args: Parameters<typeof fetch>) => Promise<Response>>(() =>
        Promise.resolve(new Response("{}", { status: 200 }))
    )
    globalThis.fetch = fetchMock as unknown as typeof fetch
    return () => fetchMock.mock.calls.map(([path]) => String(path))
}

afterEach(() => {
    globalThis.fetch = realFetch
})

const HOSTILE = "../a/b?c#d"
const ENCODED = "..%2Fa%2Fb%3Fc%23d"

describe("gateway admin paths", () => {
    test("encodes every identifier placed in a path", async () => {
        const paths = recordPaths()
        await gatewayAdmin.payment(HOSTILE)
        await gatewayAdmin.attest(HOSTILE, {
            person: "admin",
            status: "succeeded",
            evidence: "receipt",
        })
        await gatewayAdmin.revokeKey(HOSTILE)
        await gatewayAdmin.accountProviders(HOSTILE)
        await gatewayAdmin.removeEndpoint(HOSTILE)
        await gatewayAdmin.revokeRevision(HOSTILE, HOSTILE, HOSTILE)
        await gatewayAdmin.switchMode(HOSTILE, HOSTILE, "platform")
        expect(paths()).toEqual([
            `/gateway/admin/payments/${ENCODED}`,
            `/gateway/admin/payments/${ENCODED}/attestations`,
            `/gateway/admin/keys/${ENCODED}/revoke`,
            `/gateway/admin/accounts/${ENCODED}/providers`,
            `/gateway/admin/webhooks/endpoints/${ENCODED}`,
            `/gateway/admin/accounts/${ENCODED}/providers/${ENCODED}/revisions/${ENCODED}/revoke`,
            `/gateway/admin/accounts/${ENCODED}/providers/${ENCODED}/mode`,
        ])
    })
})
