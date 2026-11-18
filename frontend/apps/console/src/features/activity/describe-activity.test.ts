import { describe, expect, test } from "bun:test"
import { en as copy } from "@workspace/design-system"
import type { ActivityItem } from "@workspace/types"

import { en as t } from "@/i18n/en"

import { describeActivity } from "./describe-activity"

const payment = {
    id: "pay_1",
    reference: "ORDER-1",
    provider: "moncash",
    amount: { amount: 10_000, currency: "HTG" as const },
    status: "succeeded" as const,
}

const item = (fields: Partial<ActivityItem>): ActivityItem => ({
    id: "act_1",
    at: "2026-11-18T12:00:00Z",
    kind: "transition",
    payment,
    ...fields,
})

const names = { moncash: "MonCash" }

describe("describe activity", () => {
    test("a transition names the reference, the service and both statuses", () => {
        const row = describeActivity(
            item({ source: "signed_callback", from_status: "pending", to_status: "succeeded" }),
            t,
            copy,
            names
        )
        expect(row).toEqual({
            id: "act_1",
            at: "2026-11-18T12:00:00Z",
            reference: "pay_1",
            tag: "signed callback",
            tone: "positive",
            title: "ORDER-1 on MonCash: pending → succeeded",
        })
    })

    test("a creation without a previous status shows the new status only", () => {
        const row = describeActivity(item({ source: "creation" }), t, copy, {})
        expect(row.title).toBe("ORDER-1 on moncash: pending")
        expect(row.tone).toBe("pending")
    })

    test("a failed transition is critical", () => {
        const row = describeActivity(item({ to_status: "failed" }), t, copy, names)
        expect(row.tone).toBe("critical")
    })

    test("a delivery below 300 is positive and reports the attempt", () => {
        const row = describeActivity(
            item({
                kind: "delivery",
                event_type: "payment.succeeded",
                endpoint: "shop",
                status_code: 204,
                attempt: 2,
                duration_ms: 35,
            }),
            t,
            copy,
            names
        )
        expect(row.tone).toBe("positive")
        expect(row.title).toBe("ORDER-1 payment.succeeded → shop")
        expect(row.detail).toBe("attempt 2: 204 in 35 ms")
    })

    test("a delivery without an answer is critical", () => {
        const row = describeActivity(item({ kind: "delivery", result: "timeout" }), t, copy, names)
        expect(row.tone).toBe("critical")
        expect(row.detail).toBe("attempt 1: timeout in 0 ms")
    })

    test("a rejected sign-in is critical and a revoked key is neutral", () => {
        const signIn = describeActivity(
            item({ kind: "admin", source: "admin_sign_in_failure", payment: undefined }),
            t,
            copy,
            names
        )
        expect(signIn.tone).toBe("critical")
        expect(signIn.title).toBe("Administrator sign-in rejected")
        const key = describeActivity(
            item({
                kind: "key",
                source: "key_revoked",
                subject: "ofsp_test_ab",
                payment: undefined,
            }),
            t,
            copy,
            names
        )
        expect(key.tone).toBe("neutral")
        expect(key.title).toBe("API key ofsp_test_ab revoked")
    })

    test("a webhook change names the endpoint", () => {
        const row = describeActivity(
            item({ kind: "admin", source: "webhook_removed", subject: "shop", payment: undefined }),
            t,
            copy,
            names
        )
        expect(row.title).toBe("Webhook shop removed")
    })
})
