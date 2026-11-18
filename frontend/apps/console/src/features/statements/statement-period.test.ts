import { describe, expect, test } from "bun:test"

import { dayPeriod, isPeriodMode, MAX_RANGE_DAYS, rangePeriod } from "./statement-period"

describe("statement period", () => {
    test("a valid day asks for that business date", () => {
        expect(dayPeriod("2026-11-18")).toEqual({ params: { business_date: "2026-11-18" } })
    })

    test("an incomplete or impossible day is refused", () => {
        expect(dayPeriod("")).toEqual({ problem: "incomplete" })
        expect(dayPeriod("2026-11")).toEqual({ problem: "incomplete" })
        expect(dayPeriod("2026-13-40")).toEqual({ problem: "incomplete" })
    })

    test("a range of one day up to the limit is accepted", () => {
        expect(rangePeriod("2026-11-18", "2026-11-18")).toEqual({
            params: { from: "2026-11-18", to: "2026-11-18" },
        })
        expect(rangePeriod("2026-10-01", "2026-10-31")).toEqual({
            params: { from: "2026-10-01", to: "2026-10-31" },
        })
        expect(MAX_RANGE_DAYS).toBe(31)
    })

    test("a reversed, too long or incomplete range is refused", () => {
        expect(rangePeriod("2026-11-18", "2026-11-17")).toEqual({ problem: "reversed" })
        expect(rangePeriod("2026-10-01", "2026-11-01")).toEqual({ problem: "too-long" })
        expect(rangePeriod("2026-11-18", "")).toEqual({ problem: "incomplete" })
    })

    test("only the day and range modes exist", () => {
        expect(isPeriodMode("day")).toBe(true)
        expect(isPeriodMode("range")).toBe(true)
        expect(isPeriodMode("month")).toBe(false)
    })
})
