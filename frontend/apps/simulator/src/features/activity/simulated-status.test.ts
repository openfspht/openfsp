import { describe, expect, test } from "bun:test"
import type { SimulatedPayment } from "@workspace/types"

import { simulatedStatus } from "./simulated-payments-table"

const payment = (outcome?: SimulatedPayment["outcome"]): SimulatedPayment => ({
    operator: "mock_moncash",
    payment_id: "sim_1",
    order_id: "order_1",
    amount: "100.00",
    scenario: "SUCCESS",
    created_at: "2026-11-18T12:00:00Z",
    expires_at: "2026-11-18T12:15:00Z",
    outcome,
    callbacks_sent: 0,
    lookups: 0,
})

describe("simulated status", () => {
    test("a payment without an outcome is pending", () => {
        expect(simulatedStatus(payment())).toBe("pending")
    })

    test("each outcome maps to its OpenFSP status", () => {
        expect(simulatedStatus(payment("SUCCESSFUL"))).toBe("succeeded")
        expect(simulatedStatus(payment("FAILED"))).toBe("failed")
        expect(simulatedStatus(payment("EXPIRED"))).toBe("expired")
    })
})
