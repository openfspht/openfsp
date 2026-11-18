import { describe, expect, test } from "bun:test"
import { ACTIVITY_KINDS } from "@workspace/types"

import { ACTIVITY_FILTERS, isActivityFilter, kindsOf, sourcesOf } from "./activity-filters"

describe("activity filters", () => {
    test("all covers every kind without restricting the source", () => {
        expect(kindsOf("all")).toEqual(ACTIVITY_KINDS)
        expect(sourcesOf("all")).toEqual([])
    })

    test("callbacks keep the signed callbacks and the payment URL notifications", () => {
        expect(kindsOf("callbacks")).toEqual(["transition"])
        expect(sourcesOf("callbacks")).toEqual(["signed_callback", "per_payment_url"])
    })

    test("access groups the keys and the administrator events", () => {
        expect(kindsOf("access")).toEqual(["key", "admin"])
    })

    test("accepts only a known filter", () => {
        ACTIVITY_FILTERS.forEach((filter) => expect(isActivityFilter(filter)).toBe(true))
        expect(isActivityFilter("statements")).toBe(false)
        expect(isActivityFilter("")).toBe(false)
        expect(isActivityFilter(null)).toBe(false)
    })
})
