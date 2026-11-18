import { afterEach, describe, expect, mock, test } from "bun:test"
import type { SimulatedOperator } from "@workspace/types"

import { simulatorControl } from "./simulator-control"

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

describe("simulator control paths", () => {
    test("encodes every identifier placed in a path", async () => {
        const paths = recordPaths()
        const operator = HOSTILE as SimulatedOperator
        await simulatorControl.scenarios(operator)
        await simulatorControl.defaultScenario(operator)
        await simulatorControl.setAutomatic(operator, true)
        await simulatorControl.setDefaultScenario(operator, "SUCCESS")
        await simulatorControl.resolve(HOSTILE, "SUCCESSFUL")
        await simulatorControl.deliverCallback(HOSTILE)
        expect(paths()).toEqual([
            `/simulator/control/operators/${ENCODED}/scenarios`,
            `/simulator/control/operators/${ENCODED}/default-scenario`,
            `/simulator/control/operators/${ENCODED}/automatic`,
            `/simulator/control/operators/${ENCODED}/default-scenario`,
            `/simulator/control/payments/${ENCODED}/resolve`,
            `/simulator/control/payments/${ENCODED}/callback`,
        ])
    })
})
