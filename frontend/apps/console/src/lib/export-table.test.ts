import { describe, expect, test } from "bun:test"

import { printable, stamped } from "./export-table"

describe("table export", () => {
    test("replaces the non-breaking spaces that the PDF font cannot draw", () => {
        expect(printable("1\u202F000,00\u00A0HTG")).toBe("1 000,00 HTG")
        expect(printable("12\u2007345")).toBe("12 345")
        expect(printable(42)).toBe("42")
    })

    test("stamps the file name with the export date", () => {
        expect(stamped("payments", new Date("2026-11-18T23:00:00Z"))).toBe("payments-2026-11-18")
    })
})
