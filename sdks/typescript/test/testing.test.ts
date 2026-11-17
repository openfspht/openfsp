import { describe, expect, test } from "bun:test"
import { wireEvent, wirePayment } from "../src/testing"

describe("testing fixtures", () => {
    test("give a pending payment the next action the specification requires", () => {
        expect(wirePayment().next_action).toEqual({ type: "payer_approval" })
        expect(wirePayment({ status: "succeeded" }).next_action).toBeUndefined()
    })

    test("give a created event no previous status", () => {
        const created = wireEvent({ data: wirePayment() })
        expect(created.type).toBe("payment.created")
        expect(created.previous_status).toBeUndefined()
        expect(wireEvent().previous_status).toBe("pending")
    })
})
