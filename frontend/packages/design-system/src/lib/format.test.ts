import { describe, expect, test } from "bun:test"

import { formatDate, formatDateTime, formatMoney } from "./format"

const spaces = (value: string) => value.replace(/[\u00A0\u202F]/g, " ")

describe("format", () => {
    test("money is shown in gourdes from minor units", () => {
        expect(spaces(formatMoney({ amount: 123_456, currency: "HTG" }))).toBe("HTG 1,234.56")
        expect(spaces(formatMoney({ amount: 123_456, currency: "HTG" }, "fr-FR"))).toBe(
            "1 234,56 HTG"
        )
    })

    test("a business date is read as a calendar day, not as UTC midnight", () => {
        expect(formatDate("2026-11-01")).toBe("1 Nov 2026")
    })

    test("a timestamp keeps its date and time", () => {
        expect(formatDateTime("2026-11-18T12:34:56Z")).toContain("2026")
    })
})
